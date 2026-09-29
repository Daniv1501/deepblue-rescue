# DeepBlue Rescue

## 1. Nombre del proyecto

**DeepBlue Rescue** — capa de persistencia para una plataforma de rescate y
rehabilitación de fauna marina.

## 2. Descripción breve

DeepBlue Rescue modela el recorrido completo de un caso de rescate — desde que un animal
es admitido en un centro hasta que recibe tratamientos de especialistas — usando
**Java 21**, **Spring Boot 4**, **Spring Data JPA / Hibernate**, **Flyway**, **MapStruct** y
**PostgreSQL**, con pruebas de integración reales contra PostgreSQL mediante
**Testcontainers** y pruebas unitarias de la capa de servicio con **Mockito**.

El proyecto incluye la capa de persistencia (entidades, repositorios y migraciones) y una
**capa de servicio** con las reglas de negocio, DTOs de entrada/salida (`record`) y mappers
generados con MapStruct. Todavía **no incluye controladores REST**, seguridad ni frontend:
los servicios se pueden usar desde otras capas o desde los tests, pero no están expuestos
por HTTP.

## 3. Modelo de datos

| Entidad | Tabla | Descripción |
|---|---|---|
| `RescueCenter` | `rescue_centers` | Centro de rescate (código, nombre, ciudad) |
| `RescueCase` | `rescue_cases` | Caso de rescate abierto por un centro (código, fecha, ubicación, estado) |
| `Animal` | `animals` | Animal rescatado, asociado a un único caso |
| `MedicalRecord` | `medical_records` | Expediente médico inicial de un animal (peso, condición, lesiones) |
| `Specialist` | `specialists` | Especialista veterinario (código profesional, nombre, email, activo) |
| `Expertise` | `expertise` | Catálogo de áreas de especialización |
| `Treatment` | `treatments` | Tratamiento realizado a un animal por un especialista |

Tabla intermedia: `specialist_expertise` (relación N:M entre `Specialist` y `Expertise`).

Enumerados (persistidos como `VARCHAR` vía `@Enumerated(EnumType.STRING)`):

- `RescueStatus`: `ADMITTED`, `UNDER_EVALUATION`, `IN_REHABILITATION`, `READY_FOR_RELEASE`, `RELEASED`, `CLOSED`
- `AnimalSex`: `MALE`, `FEMALE`, `UNKNOWN`
- `TreatmentType`: `WOUND_CARE`, `HYDRATION`, `MEDICATION`, `SURGERY`, `NUTRITION`, `PHYSIOTHERAPY`, `OBSERVATION`

## 4. Relaciones

```
RescueCenter (1) ──── (N) RescueCase (1) ──── (1) Animal (1) ──── (1) MedicalRecord
                                                    │
                                                    │ (1)
                                                    │
                                                    (N)
                                                Treatment (N) ──── (1) Specialist (N) ──── (N) Expertise
```

| Relación | Tipo | Lado dueño de la FK | Cascade |
|---|---|---|---|
| `RescueCenter` → `RescueCase` | 1:N | `RescueCase` (`rescue_center_id`) | Ninguno |
| `RescueCase` → `Animal` | 1:1 | `Animal` (`rescue_case_id`, `UNIQUE`) | `ALL` desde `RescueCase.animal` |
| `Animal` → `MedicalRecord` | 1:1 | `MedicalRecord` (`animal_id`, `UNIQUE`) | `ALL` desde `Animal.medicalRecord` |
| `Animal` → `Treatment` | 1:N | `Treatment` (`animal_id`) | `ALL` desde `Animal.treatments` |
| `Specialist` → `Treatment` | 1:N | `Treatment` (`specialist_id`) | Ninguno |
| `Specialist` ↔ `Expertise` | N:M | `Specialist` (`@JoinTable specialist_expertise`) | Ninguno |

**Nota sobre el orden de construcción**: como `RescueCase`, `Animal`, `MedicalRecord` y
`Treatment` exigen la entidad "padre" ya construida en su propio constructor, el orden de
persistencia siempre es de arriba hacia abajo: primero se guarda el padre (para que tenga
`id`), y solo entonces se construye y guarda el hijo referenciándolo. Donde sí existe
`cascade = ALL` (`RescueCase.animal`, `Animal.medicalRecord`, `Animal.treatments`), guardar
el padre ya persiste el hijo automáticamente.

## 5. Instrucciones para ejecutar la aplicación

Requiere una instancia de PostgreSQL disponible. Por defecto se conecta a
`jdbc:postgresql://localhost:5432/deepblue` con usuario y clave `postgres`; se puede
sobreescribir con las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`.

```bash
mvn spring-boot:run
```

Al arrancar, Flyway ejecuta automáticamente las migraciones (`V1`, `V2`, `V3`) contra la
base configurada, y Hibernate valida (`ddl-auto: validate`) que las entidades coincidan
exactamente con el esquema ya creado.

## 6. Instrucciones para ejecutar los tests

```bash
mvn clean test
```

Hay dos tipos de pruebas:

| Clase | Tipo | Qué valida |
|---|---|---|
| `PersistenceIntegrationTest` | Integración (Testcontainers + PostgreSQL) | Mapeo de entidades, relaciones, cascadas, restricciones del esquema y query methods / consultas JPQL |
| `AnimalServiceImplTest` | Unitaria (Mockito) | Búsqueda por código, animales en rehabilitación y `canReceiveTreatment` según el estado del caso |
| `RescueCaseServiceImplTest` | Unitaria (Mockito) | Búsqueda por código o estado y cambio de estado (transición válida e inválida) |
| `TreatmentServiceImplTest` | Unitaria (Mockito) | Registro de tratamientos y cada una de las reglas de negocio que lo rechazan |

Las pruebas unitarias no necesitan base de datos. Para las de integración no se requiere
ninguna base local: **Testcontainers** levanta automáticamente un contenedor Docker con
PostgreSQL (ver sección 8), así que solo se necesita tener **Docker** corriendo en la máquina.

Para ver el contenedor mientras corren los tests:

```bash
docker ps
```

## 7. Flyway

Flyway es el único responsable de crear y evolucionar el esquema de la base de datos.
Hibernate **nunca** genera DDL (`ddl-auto: validate`): en el arranque solo compara las
entidades `@Entity` contra las tablas que Flyway ya creó, y falla si hay una discrepancia
real de columnas o tipos.

Las migraciones viven en `src/main/resources/db/migration` y se ejecutan en orden de
versión, una sola vez cada una (Flyway registra el historial en la tabla
`flyway_schema_history`):

| Migración | Contenido |
|---|---|
| `V1__create_schema.sql` | Crea las 7 tablas de negocio + la tabla intermedia `specialist_expertise`, con sus `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `CHECK` (sobre `rescue_cases.status`) e índices sobre FKs y columnas de filtrado frecuente |
| `V2__insert_expertise_catalog.sql` | Inserta el catálogo inicial de áreas de especialización (`Trauma`, `Rehabilitation`, `Marine Mammals`, etc.) |
| `V3__add_tracking_device_to_animal.sql` | Agrega la columna opcional `tracking_device_code` (nullable, `UNIQUE`) a `animals`, para el requerimiento posterior de dispositivos de seguimiento GPS |

## 8. Testcontainers

`PersistenceIntegrationTest` usa `@Testcontainers` + `@SpringBootTest` + `@Transactional`
para ejecutar pruebas de integración contra una instancia **real** de PostgreSQL, nunca una
base en memoria como H2. Esto garantiza que los `CHECK`, `UNIQUE`, tipos de columna y el
comportamiento real de Hibernate/PostgreSQL se validan contra el mismo motor de base de
datos que se usará en producción.

```java
@Container
@ServiceConnection
static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("deepblue_test")
                .withUsername("deepblue")
                .withPassword("deepblue");
```

- `@Container` delega a Testcontainers el ciclo de vida del contenedor (se levanta antes de
  la clase de test y se destruye al finalizar).
- `@ServiceConnection` conecta automáticamente el `DataSource` de Spring Boot al contenedor,
  sin necesidad de declarar propiedades manualmente con `@DynamicPropertySource`.
- Cada método de test corre dentro de una transacción (`@Transactional`) que se revierte al
  finalizar, así que los tests no interfieren entre sí aunque compartan el mismo contenedor.
- Flyway corre sus migraciones automáticamente contra el contenedor al arrancar el contexto
  de Spring, igual que en un entorno real.

## 9. Query Methods implementados

| Repository | Método | Propósito |
|---|---|---|
| `RescueCenterRepository` | `findByCode(String code)` | Buscar un centro por su código |
| `RescueCaseRepository` | `findByCaseCode(String caseCode)` | Buscar un caso por su código |
| `RescueCaseRepository` | `findByStatusOrderByRescueDateAsc(RescueStatus status)` | Casos con determinado estado, ordenados por fecha ascendente |
| `RescueCaseRepository` | `findByRescueCenter_Code(String rescueCenterCode)` | Casos pertenecientes a un centro (navegando `rescueCenter.code`) |
| `RescueCaseRepository` | `findByRescueDateAfterOrderByRescueDateDesc(LocalDate rescueDate)` | Casos posteriores a una fecha, del más reciente al más antiguo |
| `AnimalRepository` | `findByAnimalCode(String animalCode)` | Buscar un animal por su código |
| `AnimalRepository` | `findByCommonNameContainingIgnoreCase(String commonName)` | Búsqueda parcial por nombre común, sin distinguir mayúsculas/minúsculas |
| `AnimalRepository` | `findByRescueCase_Status(RescueStatus status)` | Animales cuyo caso de rescate tiene determinado estado |
| `AnimalRepository` | `findByRescueCase_RescueCenter_Code(String centerCode)` | Animales pertenecientes a un centro (navegando dos relaciones) |
| `ExpertiseRepository` | `findByNameIgnoreCase(String name)` | Buscar una expertise por nombre, sin distinguir mayúsculas/minúsculas |
| `TreatmentRepository` | `findByAnimal_IdOrderByPerformedAtAsc(Long animalId)` | Tratamientos de un animal, ordenados cronológicamente |
| `TreatmentRepository` | `findByAnimal_AnimalCodeOrderByPerformedAtAsc(String animalCode)` | Igual que el anterior, pero buscando por el código de negocio del animal (lo usa `TreatmentService.findByAnimalCode`) |
| `SpecialistRepository` | `findByProfessionalCode(String professionalCode)` | Buscar un especialista por su código profesional (lo usa `TreatmentService.register`) |

## 10. Consultas JPQL implementadas (`@Query`)

| Repository | Método | JPQL | Propósito |
|---|---|---|---|
| `SpecialistRepository` | `findActiveByExpertise(String expertiseName)` | `select distinct s from Specialist s join s.expertiseAreas e where s.active = true and lower(e.name) = lower(:expertiseName) order by s.lastName asc, s.firstName asc` | Especialistas activos que tienen determinada área de especialización |
| `TreatmentRepository` | `findBetween(LocalDateTime start, LocalDateTime end)` | `select t from Treatment t where t.performedAt between :start and :end order by t.performedAt asc` | Tratamientos realizados dentro de un intervalo de fechas |
| `TreatmentRepository` | `findByRescueCenterCode(String centerCode)` | `select t from Treatment t join t.animal a join a.rescueCase rescueCase join rescueCase.rescueCenter center where center.code = :centerCode` | Tratamientos de animales pertenecientes a un centro (recorre `Treatment → Animal → RescueCase → RescueCenter`) |
| `TreatmentRepository` | `findBySpecialistExpertise(String expertiseName)` | `select distinct t from Treatment t join t.specialist specialist join specialist.expertiseAreas expertise where expertise.name = :expertiseName` | Tratamientos realizados por especialistas con determinada área de especialización (relación N:M, `DISTINCT` para evitar duplicados) |

## 11. Capa de servicio

Los servicios son interfaces en `service/` con su implementación en `service/impl/`. Las
implementaciones están anotadas con `@Transactional(readOnly = true)` a nivel de clase, y
los métodos que escriben (`changeStatus`, `register`) sobreescriben esa configuración con
`@Transactional`. Los servicios nunca devuelven entidades: devuelven DTOs.

| Servicio | Método | Propósito |
|---|---|---|
| `AnimalService` | `findByCode(animalCode)` | Obtener un animal por su código |
| `AnimalService` | `findAnimalsInRehabilitation()` | Animales cuyo caso está en `IN_REHABILITATION` |
| `AnimalService` | `canReceiveTreatment(animalCode)` | Indica si el animal puede recibir tratamientos según el estado de su caso |
| `RescueCaseService` | `findByCode(caseCode)` | Obtener un caso por su código |
| `RescueCaseService` | `findByStatus(status)` | Casos con determinado estado, ordenados por fecha ascendente |
| `RescueCaseService` | `changeStatus(caseCode, request)` | Cambiar el estado de un caso validando la transición |
| `TreatmentService` | `register(request)` | Registrar un tratamiento validando las reglas de negocio |
| `TreatmentService` | `findByAnimalCode(animalCode)` | Tratamientos de un animal, en orden cronológico |

### Reglas de negocio

**Transiciones de estado de un caso** (`RescueCaseService.changeStatus`). Solo se permite
avanzar un paso a la vez:

```
ADMITTED → UNDER_EVALUATION → IN_REHABILITATION → READY_FOR_RELEASE → RELEASED
```

Cualquier otra transición (saltarse un paso, retroceder, o salir de `RELEASED` / `CLOSED`)
lanza `BusinessRuleException`. Si el caso no existe, lanza `ResourceNotFoundException`.

**Registro de tratamientos** (`TreatmentService.register`). Se validan, en este orden:

1. El animal debe existir (`ResourceNotFoundException`).
2. El especialista debe existir (`ResourceNotFoundException`).
3. El especialista debe estar activo (`BusinessRuleException`).
4. El caso del animal no puede estar `RELEASED` ni `CLOSED` (`BusinessRuleException`).
5. La fecha del tratamiento (`performedAt`) no puede ser anterior a la fecha del rescate
   (`BusinessRuleException`).

**Tratamientos permitidos según el estado** (`AnimalService.canReceiveTreatment`). Un animal
solo puede recibir tratamientos cuando su caso está en `UNDER_EVALUATION` o
`IN_REHABILITATION`.

## 12. DTOs, mappers y excepciones

**DTOs** (`dto/`, todos `record`):

| DTO | Uso | Campos |
|---|---|---|
| `CreateTreatmentRequest` | Entrada de `TreatmentService.register` | `animalCode`, `specialistCode`, `performedAt`, `type`, `description` |
| `ChangeRescueStatusRequest` | Entrada de `RescueCaseService.changeStatus` | `status` |
| `AnimalResponse` | Salida | `id`, `animalCode`, `commonName`, `scientificName`, `sex`, `caseCode`, `rescueStatus` |
| `RescueCaseResponse` | Salida | `id`, `caseCode`, `rescueDate`, `rescueLocation`, `status`, `centerCode`, `animalCode` |
| `TreatmentResponse` | Salida | `id`, `animalCode`, `specialistCode`, `performedAt`, `type`, `description` |

Los DTOs de salida exponen códigos de negocio (`caseCode`, `centerCode`, `animalCode`,
`specialistCode`) en lugar de entidades relacionadas, para evitar acoplamiento y problemas
de *lazy loading* fuera de la sesión de Hibernate.

**Mappers** (`mapper/`): `AnimalMapper`, `RescueCaseMapper` y `TreatmentMapper` son
interfaces de MapStruct con `componentModel = "spring"`. La implementación se genera en
tiempo de compilación (el procesador está configurado en el `maven-compiler-plugin` del
`pom.xml`).

**Excepciones** (`exception/`):

| Excepción | Cuándo se lanza |
|---|---|
| `ResourceNotFoundException` | El recurso solicitado no existe (por ejemplo, `Animal AN-999 does not exist.`) |
| `BusinessRuleException` | El recurso existe, pero la operación viola una regla de negocio |

Ambas extienden `RuntimeException`.

## Estructura del proyecto

```
deepblue-rescue/
├── pom.xml
├── README.md
├── src/main
│   ├── java/com/deepblue/rescue
│   │   ├── DeepBlueRescueApplication.java
│   │   ├── domain/        (entidades JPA + enums)
│   │   ├── repository/    (interfaces JpaRepository)
│   │   ├── service/       (interfaces de servicio)
│   │   │   └── impl/      (implementaciones con las reglas de negocio)
│   │   ├── dto/
│   │   │   ├── request/   (CreateTreatmentRequest, ChangeRescueStatusRequest)
│   │   │   └── response/  (AnimalResponse, RescueCaseResponse, TreatmentResponse)
│   │   ├── mapper/        (mappers MapStruct entidad → DTO)
│   │   └── exception/     (ResourceNotFoundException, BusinessRuleException)
│   └── resources
│       ├── application.yml
│       └── db/migration   (V1, V2, V3)
└── src/test/java/com/deepblue/rescue
    ├── PersistenceIntegrationTest.java
    └── service
        ├── AnimalServiceImplTest.java
        ├── RescueCaseServiceImplTest.java
        └── TreatmentServiceImplTest.java
```
