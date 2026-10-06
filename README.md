# DeepBlue Rescue

## 1. Nombre del proyecto

**DeepBlue Rescue** — capa de persistencia para una plataforma de rescate y
rehabilitación de fauna marina.

## 2. Descripción breve

DeepBlue Rescue modela el recorrido completo de un caso de rescate — desde que un animal
es admitido en un centro hasta que recibe tratamientos de especialistas — usando
**Java 21**, **Spring Boot 4**, **Spring Data JPA / Hibernate**, **Flyway** y
**PostgreSQL**, con pruebas de integración reales contra PostgreSQL mediante
**Testcontainers**. Incluye también la capa de servicio y la capa de controladores REST
(`/api/rescue-cases`, `/api/animals`, `/api/treatments`) con un contrato de error común
(`ErrorResponse`). Ver `RESPUESTAS_LABORATORIO_CONTROLADOR.md`.

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

No se requiere ninguna base de datos local: **Testcontainers** levanta automáticamente un
contenedor Docker con PostgreSQL para la ejecución de las pruebas (ver sección 8). Solo se
necesita tener **Docker** corriendo en la máquina.

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

## 10. Consultas JPQL implementadas (`@Query`)

| Repository | Método | JPQL | Propósito |
|---|---|---|---|
| `SpecialistRepository` | `findActiveByExpertise(String expertiseName)` | `select distinct s from Specialist s join s.expertiseAreas e where s.active = true and lower(e.name) = lower(:expertiseName) order by s.lastName asc, s.firstName asc` | Especialistas activos que tienen determinada área de especialización |
| `TreatmentRepository` | `findBetween(LocalDateTime start, LocalDateTime end)` | `select t from Treatment t where t.performedAt between :start and :end order by t.performedAt asc` | Tratamientos realizados dentro de un intervalo de fechas |
| `TreatmentRepository` | `findByRescueCenterCode(String centerCode)` | `select t from Treatment t join t.animal a join a.rescueCase rescueCase join rescueCase.rescueCenter center where center.code = :centerCode` | Tratamientos de animales pertenecientes a un centro (recorre `Treatment → Animal → RescueCase → RescueCenter`) |
| `TreatmentRepository` | `findBySpecialistExpertise(String expertiseName)` | `select distinct t from Treatment t join t.specialist specialist join specialist.expertiseAreas expertise where expertise.name = :expertiseName` | Tratamientos realizados por especialistas con determinada área de especialización (relación N:M, `DISTINCT` para evitar duplicados) |

## Estructura del proyecto

```
deepblue-rescue/
├── pom.xml
├── README.md
├── src/main
│   ├── java/com/deepblue/rescue
│   │   ├── DeepBlueRescueApplication.java
│   │   ├── domain/        (entidades JPA + enums)
│   │   └── repository/    (interfaces JpaRepository)
│   └── resources
│       ├── application.yml
│       └── db/migration   (V1, V2, V3)
└── src/test
    └── java/com/deepblue/rescue
        └── PersistenceIntegrationTest.java
```
