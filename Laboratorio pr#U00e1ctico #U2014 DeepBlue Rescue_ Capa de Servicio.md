# Laboratorio práctico — DeepBlue Rescue

## Implementación de la capa de servicio con Spring Boot 4

**Duración máxima:** 2 horas  
**Nivel:** Básico → Intermedio  
**Tecnologías:** Java 21, Spring Boot 4, Spring Data JPA, MapStruct, JUnit, Mockito y AssertJ  
**Modalidad:** Individual o parejas

---

# 1. Contexto

En el laboratorio anterior construimos la capa de persistencia de **DeepBlue Rescue**, una plataforma para administrar el rescate y rehabilitación de fauna marina.

El proyecto actualmente dispone de entidades como:

```text
RescueCenter
RescueCase
Animal
MedicalRecord
Specialist
Expertise
Treatment
```

y repositories como:

```text
RescueCenterRepository
RescueCaseRepository
AnimalRepository
MedicalRecordRepository
SpecialistRepository
ExpertiseRepository
TreatmentRepository
```

Hasta ahora nuestra aplicación puede:

```text
Service inexistente

        ↓

Repository

        ↓

Spring Data JPA

        ↓

Hibernate

        ↓

PostgreSQL
```

En este laboratorio agregaremos una nueva capa:

```text
Controller
   ↓
   ↓  futuro
   ↓
Service
   ↓
Repository
   ↓
Hibernate
   ↓
PostgreSQL
```

El objetivo será comprender **qué responsabilidad tiene realmente una capa de servicio**.

---

# 2. Objetivos de aprendizaje

Al terminar el laboratorio podrás:

1. Explicar la responsabilidad de una capa Service.
2. Crear interfaces de servicio.
3. Implementar servicios con `@Service`.
4. Aplicar inyección de dependencias mediante constructor.
5. Utilizar DTOs definidos con Java `record`.
6. Utilizar MapStruct para transformar Entity ↔ DTO.
7. Utilizar `Optional` apropiadamente.
8. Crear excepciones personalizadas.
9. Aplicar reglas de negocio.
10. Utilizar `@Transactional`.
11. Diferenciar operaciones de lectura y escritura.
12. Crear unit tests para servicios.
13. Utilizar Mockito para reemplazar repositories.
14. Utilizar AssertJ para verificar resultados.
15. Diferenciar un test unitario de un test de integración.

---

# 3. ¿Qué problema resuelve la capa Service?

Supongamos que necesitamos registrar un tratamiento.

A primera vista podríamos escribir:

```java
treatmentRepository.save(treatment);
```

Pero antes de guardar existen varias preguntas:

```text
¿Existe el animal?

¿Existe el especialista?

¿El especialista está activo?

¿El animal continúa en rehabilitación?

¿La fecha del tratamiento es válida?

¿El caso ya fue liberado?

¿Quién transforma el resultado a un DTO?
```

Estas reglas no deberían estar dentro del Controller.

Tampoco deberían ser responsabilidad del Repository.

La capa Service permite implementar:

```text
                 SERVICE
                    │
        ┌───────────┼─────────────┐
        │           │             │
        ▼           ▼             ▼
 reglas negocio  repositories   transacciones
        │
        ▼
      DTOs
```

---

# 4. Arquitectura del laboratorio

Al finalizar tendremos:

```text
                Service Interface
                       │
                       ▼
               ServiceImpl
                  /       \
                 /         \
                ▼           ▼
          Repository      Mapper
                │           │
                ▼           ▼
             Entity        DTO
                │
                ▼
             Database
```

Por ejemplo:

```text
TreatmentService
        │
        ▼
TreatmentServiceImpl
        │
        ├──────────────► AnimalRepository
        │
        ├──────────────► SpecialistRepository
        │
        ├──────────────► TreatmentRepository
        │
        └──────────────► TreatmentMapper
```

---

# 5. Alcance

Durante las dos horas implementaremos principalmente:

```text
RescueCaseService
TreatmentService
```

Trabajaremos con:

```text
DTO records

MapStruct

Optional

Custom Exceptions

Business Rules

@Transactional

JUnit

Mockito

AssertJ
```

No implementaremos todavía:

```text
Controllers
REST API
Spring Security
GlobalExceptionHandler
Integration tests
Testcontainers
```

Estos temas pertenecen a laboratorios posteriores.

---

# PARTE I — PREPARACIÓN

# 6. Paso 1 — Crear la estructura

Agregar al proyecto:

```text
src/main/java/com/deepblue/rescue
│
├── domain
│
├── repository
│
├── dto
│   ├── request
│   │   ├── ChangeRescueStatusRequest.java
│   │   └── CreateTreatmentRequest.java
│   │
│   └── response
│       ├── RescueCaseResponse.java
│       └── TreatmentResponse.java
│
├── mapper
│   ├── RescueCaseMapper.java
│   └── TreatmentMapper.java
│
├── exception
│   ├── ResourceNotFoundException.java
│   └── BusinessRuleException.java
│
└── service
    ├── RescueCaseService.java
    ├── TreatmentService.java
    │
    └── impl
        ├── RescueCaseServiceImpl.java
        └── TreatmentServiceImpl.java
```

Para pruebas:

```text
src/test/java/com/deepblue/rescue/service

├── RescueCaseServiceImplTest.java
└── TreatmentServiceImplTest.java
```

---

# 7. Paso 2 — Agregar MapStruct

Agregar al `pom.xml`:

```xml
<properties>

    <java.version>21</java.version>

    <mapstruct.version>1.6.3</mapstruct.version>

</properties>
```

Dependencia:

```xml
<dependency>

    <groupId>org.mapstruct</groupId>

    <artifactId>mapstruct</artifactId>

    <version>${mapstruct.version}</version>

</dependency>
```

Agregar el annotation processor:

```xml
<plugin>

    <groupId>org.apache.maven.plugins</groupId>

    <artifactId>maven-compiler-plugin</artifactId>

    <configuration>

        <annotationProcessorPaths>

            <path>

                <groupId>org.mapstruct</groupId>

                <artifactId>mapstruct-processor</artifactId>

                <version>${mapstruct.version}</version>

            </path>

        </annotationProcessorPaths>

    </configuration>

</plugin>
```

Ejecutar:

```bash
mvn clean compile
```

---

# 8. Paso 3 — Verificar dependencia de testing

El proyecto debe tener:

```xml
<dependency>

    <groupId>org.springframework.boot</groupId>

    <artifactId>spring-boot-starter-test</artifactId>

    <scope>test</scope>

</dependency>
```

Utilizaremos:

```text
JUnit

Mockito

AssertJ
```

No necesitamos agregar Mockito manualmente.

---

# PARTE II — DTO

# 9. Paso 4 — ¿Por qué no retornar entidades?

Podríamos escribir:

```java
public RescueCase findByCode(String code)
```

pero exponer directamente las entidades provoca un acoplamiento innecesario entre:

```text
modelo persistente

y

modelo utilizado por otras capas
```

Utilizaremos:

```text
Entity
   ↓
Mapper
   ↓
DTO
```

---

# 10. Paso 5 — Crear RescueCaseResponse

Crear:

```java
package com.deepblue.rescue.dto.response;

import com.deepblue.rescue.domain.RescueStatus;

import java.time.LocalDate;

public record RescueCaseResponse(

        Long id,

        String caseCode,

        LocalDate rescueDate,

        String rescueLocation,

        RescueStatus status,

        String centerCode,

        String animalCode

) {
}
```

Observe que el DTO no contiene:

```java
RescueCenter rescueCenter;
Animal animal;
```

Contiene únicamente:

```text
centerCode

animalCode
```

---

# 11. ¿Por qué utilizar record?

Un DTO tradicional requeriría:

```text
campos
constructor
getters
equals
hashCode
toString
```

Con Java `record` podemos declarar:

```java
public record ExampleResponse(
        Long id,
        String name
) {
}
```

de una forma mucho más compacta.

---

# 12. Paso 6 — Crear ChangeRescueStatusRequest

```java
package com.deepblue.rescue.dto.request;

import com.deepblue.rescue.domain.RescueStatus;

public record ChangeRescueStatusRequest(

        RescueStatus status

) {
}
```

---

# 13. Paso 7 — Crear CreateTreatmentRequest

```java
package com.deepblue.rescue.dto.request;

import com.deepblue.rescue.domain.TreatmentType;

import java.time.LocalDateTime;

public record CreateTreatmentRequest(

        String animalCode,

        String specialistCode,

        LocalDateTime performedAt,

        TreatmentType type,

        String description

) {
}
```

---

# 14. Paso 8 — Crear TreatmentResponse

Implementa:

```java
public record TreatmentResponse(...)
```

Debe contener:

```text
id

animalCode

specialistCode

performedAt

type

description
```

No debe contener directamente:

```text
Animal

Specialist
```

---

# CHECKPOINT 1

Identifica:

```text
Entity
```

como:

> objeto utilizado para representar información persistente.

y:

```text
DTO
```

como:

> objeto utilizado para transferir información entre capas.

El estudiante debe poder explicar por qué:

```text
Entity != DTO
```

---

# PARTE III — MAPSTRUCT

# 15. Paso 9 — Crear RescueCaseMapper

Crear:

```java
package com.deepblue.rescue.mapper;

import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.dto.response.RescueCaseResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RescueCaseMapper {

    @Mapping(
        target = "centerCode",
        source = "rescueCenter.code"
    )
    @Mapping(
        target = "animalCode",
        source = "animal.animalCode"
    )
    RescueCaseResponse toResponse(
            RescueCase rescueCase
    );
}
```

MapStruct generará automáticamente la implementación.

---

# 16. Paso 10 — Crear TreatmentMapper

Implementa:

```java
@Mapper(componentModel = "spring")
public interface TreatmentMapper {
```

Debe transformar:

```text
Treatment
    ↓
TreatmentResponse
```

Necesitarás mapear:

```text
animal.animalCode
        ↓
animalCode
```

y:

```text
specialist.professionalCode
        ↓
specialistCode
```

No se proporciona el mapper terminado.

---

# 17. ¿Qué problema resuelve MapStruct?

Sin mapper podríamos terminar escribiendo repetidamente:

```java
return new RescueCaseResponse(
    entity.getId(),
    entity.getCaseCode(),
    entity.getRescueDate(),
    entity.getRescueLocation(),
    entity.getStatus(),
    entity.getRescueCenter().getCode(),
    entity.getAnimal().getAnimalCode()
);
```

MapStruct automatiza esta transformación en tiempo de compilación.

---

# PARTE IV — EXCEPCIONES

# 18. Paso 11 — ResourceNotFoundException

Crear:

```java
package com.deepblue.rescue.exception;

public class ResourceNotFoundException
        extends RuntimeException {

    public ResourceNotFoundException(
            String message) {

        super(message);
    }
}
```

La utilizaremos cuando no exista un recurso.

Ejemplo:

```text
Animal AN-999 does not exist.
```

---

# 19. Paso 12 — BusinessRuleException

Crear:

```java
package com.deepblue.rescue.exception;

public class BusinessRuleException
        extends RuntimeException {

    public BusinessRuleException(
            String message) {

        super(message);
    }
}
```

Ejemplo:

```text
Cannot register treatment because
the animal has already been released.
```

---

# 20. Diferencia

`ResourceNotFoundException`:

```text
el recurso buscado no existe
```

`BusinessRuleException`:

```text
el recurso existe

PERO

la operación no está permitida
```

Ejemplo:

```text
Specialist existe
        ↓
pero está inactivo
        ↓
BusinessRuleException
```

---

# PARTE V — RESCUE CASE SERVICE

# 21. Paso 13 — Crear la interfaz

Crear:

```java
package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.response.RescueCaseResponse;

import java.util.List;

public interface RescueCaseService {

    RescueCaseResponse findByCode(
            String caseCode
    );

    List<RescueCaseResponse> findByStatus(
            RescueStatus status
    );

    RescueCaseResponse changeStatus(
            String caseCode,
            ChangeRescueStatusRequest request
    );
}
```

---

# 22. ¿Por qué utilizar interface?

Tendremos:

```text
RescueCaseService
        │
        │ contract
        ▼
RescueCaseServiceImpl
```

La interfaz define:

```text
QUÉ puede hacer el servicio
```

La implementación define:

```text
CÓMO lo hace
```

Esto reduce el acoplamiento entre capas.

---

# 23. Paso 14 — Crear RescueCaseServiceImpl

Crear:

```java
@Service
@Transactional(readOnly = true)
public class RescueCaseServiceImpl
        implements RescueCaseService {

    private final RescueCaseRepository repository;

    private final RescueCaseMapper mapper;

    public RescueCaseServiceImpl(
            RescueCaseRepository repository,
            RescueCaseMapper mapper) {

        this.repository = repository;
        this.mapper = mapper;
    }
}
```

No utilizar:

```java
@Autowired
private RescueCaseRepository repository;
```

Preferir inyección mediante constructor.

---

# 24. Paso 15 — Implementar findByCode

El repository existente debe proporcionar:

```java
Optional<RescueCase> findByCaseCode(
        String caseCode);
```

Implementar:

```java
@Override
public RescueCaseResponse findByCode(
        String caseCode) {

    return repository
            .findByCaseCode(caseCode)
            .map(mapper::toResponse)
            .orElseThrow(
                () -> new ResourceNotFoundException(
                    "Rescue case not found: "
                    + caseCode
                )
            );
}
```

---

# 25. Analizar Optional

Tenemos:

```java
repository.findByCaseCode(...)
```

que devuelve:

```java
Optional<RescueCase>
```

La secuencia:

```text
Optional<RescueCase>
        ↓
map()
        ↓
Optional<RescueCaseResponse>
        ↓
orElseThrow()
```

permite evitar:

```java
if (entity == null) {
   ...
}
```

---

# 26. Paso 16 — Implementar findByStatus

Repository:

```java
List<RescueCase>
findByStatusOrderByRescueDateAsc(
        RescueStatus status);
```

Implementar utilizando programación funcional:

```java
@Override
public List<RescueCaseResponse> findByStatus(
        RescueStatus status) {

    return repository
            .findByStatusOrderByRescueDateAsc(status)
            .stream()
            .map(mapper::toResponse)
            .toList();
}
```

Analiza:

```text
List<Entity>
     ↓
stream()
     ↓
map()
     ↓
List<DTO>
```

---

# PARTE VI — PRIMERA REGLA DE NEGOCIO

# 27. Paso 17 — Cambiar el estado de un rescate

DeepBlue define el siguiente flujo:

```text
ADMITTED
    ↓
UNDER_EVALUATION
    ↓
IN_REHABILITATION
    ↓
READY_FOR_RELEASE
    ↓
RELEASED
```

No queremos permitir:

```text
ADMITTED
    ↓
RELEASED
```

ni:

```text
RELEASED
    ↓
IN_REHABILITATION
```

---

# 28. Paso 18 — Crear validación de transición

Dentro de:

```text
RescueCaseServiceImpl
```

crear:

```java
private boolean isValidTransition(
        RescueStatus current,
        RescueStatus next) {

    return switch (current) {

        case ADMITTED ->
            next == RescueStatus.UNDER_EVALUATION;

        case UNDER_EVALUATION ->
            next == RescueStatus.IN_REHABILITATION;

        case IN_REHABILITATION ->
            next == RescueStatus.READY_FOR_RELEASE;

        case READY_FOR_RELEASE ->
            next == RescueStatus.RELEASED;

        default -> false;
    };
}
```

---

# 29. Paso 19 — Implementar changeStatus

Esta operación modifica información.

Por eso deberá utilizar:

```java
@Transactional
```

y no solamente:

```java
@Transactional(readOnly = true)
```

Implementa:

```java
@Override
@Transactional
public RescueCaseResponse changeStatus(
        String caseCode,
        ChangeRescueStatusRequest request) {

    // TODO 1
    // Buscar el RescueCase.
    
    // TODO 2
    // Si no existe:
    // ResourceNotFoundException.

    // TODO 3
    // Obtener currentStatus.

    // TODO 4
    // Validar transición.

    // TODO 5
    // Si no es válida:
    // BusinessRuleException.

    // TODO 6
    // Cambiar status.

    // TODO 7
    // Guardar.

    // TODO 8
    // Transformar a Response.
}
```

---

# 30. Resultado esperado

Una transición:

```text
ADMITTED
        ↓
UNDER_EVALUATION
```

debe funcionar.

Pero:

```text
ADMITTED
        ↓
READY_FOR_RELEASE
```

debe producir:

```java
BusinessRuleException
```

---

# PARTE VII — TREATMENT SERVICE

# 31. Paso 20 — Crear la interfaz

Crear:

```java
public interface TreatmentService {

    TreatmentResponse register(
            CreateTreatmentRequest request
    );

    List<TreatmentResponse> findByAnimalCode(
            String animalCode
    );
}
```

---

# 32. Paso 21 — Analizar register

Antes de guardar un tratamiento necesitamos:

```text
CreateTreatmentRequest
        │
        ▼
¿Existe Animal?
        │
        ▼
¿Existe Specialist?
        │
        ▼
¿Specialist está activo?
        │
        ▼
¿Animal puede recibir tratamientos?
        │
        ▼
¿Fecha es válida?
        │
        ▼
crear Treatment
        │
        ▼
save()
        │
        ▼
TreatmentResponse
```

Este flujo muestra claramente por qué necesitamos una capa Service.

---

# 33. Paso 22 — Repositories necesarios

`AnimalRepository` debe tener:

```java
Optional<Animal> findByAnimalCode(
        String animalCode);
```

`SpecialistRepository`:

```java
Optional<Specialist> findByProfessionalCode(
        String professionalCode);
```

`TreatmentRepository`:

```java
List<Treatment>
findByAnimalAnimalCodeOrderByPerformedAtAsc(
        String animalCode);
```

---

# 34. Paso 23 — Crear TreatmentServiceImpl

Crear:

```java
@Service
@Transactional(readOnly = true)
public class TreatmentServiceImpl
        implements TreatmentService {

    private final AnimalRepository animalRepository;

    private final SpecialistRepository specialistRepository;

    private final TreatmentRepository treatmentRepository;

    private final TreatmentMapper mapper;

    public TreatmentServiceImpl(
            AnimalRepository animalRepository,
            SpecialistRepository specialistRepository,
            TreatmentRepository treatmentRepository,
            TreatmentMapper mapper) {

        this.animalRepository = animalRepository;
        this.specialistRepository = specialistRepository;
        this.treatmentRepository = treatmentRepository;
        this.mapper = mapper;
    }
}
```

---

# 35. Paso 24 — Implementar búsqueda

Implementar:

```java
findByAnimalCode(...)
```

utilizando:

```text
TreatmentRepository

stream()

map()

toList()
```

La operación es de solo lectura.

---

# PARTE VIII — REGLAS DE TRATAMIENTO

# 36. Paso 25 — Implementar register

El estudiante deberá implementar la operación.

Debe cumplir estas reglas.

## Regla 1

El animal debe existir.

Si no existe:

```java
ResourceNotFoundException
```

---

## Regla 2

El especialista debe existir.

Si no existe:

```java
ResourceNotFoundException
```

---

## Regla 3

El especialista debe estar activo.

Si:

```java
specialist.isActive() == false
```

lanzar:

```java
BusinessRuleException
```

---

## Regla 4

No se pueden agregar tratamientos si el caso está:

```text
RELEASED

o

CLOSED
```

Resultado:

```java
BusinessRuleException
```

---

## Regla 5

La fecha del tratamiento no puede ser anterior a:

```text
rescueCase.rescueDate
```

Ejemplo inválido:

```text
Rescue:
2026-08-20

Treatment:
2026-08-15
```

---

# 37. Esqueleto

Implementar:

```java
@Override
@Transactional
public TreatmentResponse register(
        CreateTreatmentRequest request) {

    // 1. Buscar Animal

    // 2. Buscar Specialist

    // 3. Validar specialist.active

    // 4. Obtener RescueCase del Animal

    // 5. Validar status

    // 6. Validar performedAt

    // 7. Crear Treatment

    // 8. Guardar Treatment

    // 9. Mapear TreatmentResponse
}
```

---

# 38. Construcción del Treatment

Suponiendo que la entidad dispone de:

```java
public Treatment(
        Animal animal,
        Specialist specialist,
        LocalDateTime performedAt,
        TreatmentType type,
        String description)
```

podrás crear:

```java
Treatment treatment =
        new Treatment(
                animal,
                specialist,
                request.performedAt(),
                request.type(),
                request.description()
        );
```

---

# CHECKPOINT 2

Hasta aquí debes poder explicar por qué esta lógica:

```text
¿specialist está activo?

¿caso está RELEASED?

¿fecha es válida?
```

pertenece a:

```text
Service
```

y no a:

```text
Repository
```

---

# PARTE IX — UNIT TESTS

# 39. Paso 26 — ¿Qué probaremos?

Queremos probar:

```text
Service
```

aislado.

No queremos probar:

```text
PostgreSQL

Hibernate

Spring Data

Flyway
```

Por tanto NO utilizaremos:

```java
@SpringBootTest
```

ni:

```java
@DataJpaTest
```

Utilizaremos:

```text
JUnit
+
Mockito
```

---

# 40. Arquitectura del test

En producción:

```text
Service
   ↓
Repository real
   ↓
PostgreSQL
```

En el unit test:

```text
Service
   ↓
Repository MOCK
```

---

# 41. Paso 27 — Crear RescueCaseServiceImplTest

Crear:

```java
@ExtendWith(MockitoExtension.class)
class RescueCaseServiceImplTest {

    @Mock
    private RescueCaseRepository repository;

    @Mock
    private RescueCaseMapper mapper;

    @InjectMocks
    private RescueCaseServiceImpl service;
}
```

Imports:

```java
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
```

---

# 42. Paso 28 — Test: caso encontrado

Preparar:

```java
@Test
void shouldFindRescueCaseByCode() {

    RescueCase rescueCase = /* crear objeto */;

    RescueCaseResponse response =
            /* crear response */;

    when(
        repository.findByCaseCode("RES-001")
    ).thenReturn(
        Optional.of(rescueCase)
    );

    when(
        mapper.toResponse(rescueCase)
    ).thenReturn(response);

    RescueCaseResponse result =
            service.findByCode("RES-001");

    assertThat(result)
            .isEqualTo(response);

    verify(repository)
            .findByCaseCode("RES-001");

    verify(mapper)
            .toResponse(rescueCase);
}
```

---

# 43. Concepto Arrange — Act — Assert

La prueba tiene tres fases:

```text
ARRANGE
```

preparar:

```java
when(...)
```

---

```text
ACT
```

ejecutar:

```java
service.findByCode(...)
```

---

```text
ASSERT
```

comprobar:

```java
assertThat(...)
```

---

# 44. Paso 29 — Test: recurso inexistente

Implementar:

```text
findByCode("RES-999")
```

Repository:

```java
Optional.empty()
```

Resultado esperado:

```java
ResourceNotFoundException
```

Utilizar:

```java
assertThatThrownBy(...)
```

y comprobar además:

```java
verify(mapper, never())
        .toResponse(any());
```

---

# 45. Paso 30 — Test transición válida

Estado actual:

```text
ADMITTED
```

Solicitud:

```text
UNDER_EVALUATION
```

Debe:

```text
cambiar status

guardar

retornar response
```

Verificar que:

```java
repository.save(...)
```

fue ejecutado.

---

# 46. Paso 31 — Test transición inválida

Estado:

```text
ADMITTED
```

Solicitud:

```text
READY_FOR_RELEASE
```

Debe producir:

```java
BusinessRuleException
```

y lo más importante:

```java
verify(repository, never())
        .save(any());
```

¿Por qué?

Porque una operación que viola una regla de negocio nunca debe llegar a persistirse.

---

# PARTE X — TEST DE TREATMENT SERVICE

# 47. Paso 32 — Test de registro correcto

Escenario:

```text
Animal:
AN-001

Rescue status:
IN_REHABILITATION

Specialist:
SPEC-001

Specialist active:
true

Treatment:
WOUND_CARE
```

Configurar:

```java
when(
    animalRepository.findByAnimalCode("AN-001")
)
```

y:

```java
when(
    specialistRepository
        .findByProfessionalCode("SPEC-001")
)
```

El resultado esperado:

```text
Treatment guardado

TreatmentResponse retornado
```

---

# 48. Paso 33 — Test especialista inactivo

Escenario:

```text
Animal existe

Specialist existe

Specialist.active = false
```

Resultado:

```java
BusinessRuleException
```

Verificar:

```java
verify(
    treatmentRepository,
    never()
).save(any());
```

---

# 49. Paso 34 — Test animal liberado

Escenario:

```text
Animal:
AN-001

RescueCase.status:
RELEASED
```

Intentar registrar:

```text
OBSERVATION
```

Resultado esperado:

```java
BusinessRuleException
```

---

# PARTE XI — RETO INTEGRADOR

# 50. Escenario

DeepBlue recibe el caso:

```text
Case:
RES-2026-100

Status:
IN_REHABILITATION
```

Animal:

```text
Code:
AN-2026-100

Common name:
Green Sea Turtle

Rescue date:
2026-08-20
```

Especialista:

```text
Code:
SPEC-001

Name:
Elena Vargas

Active:
true
```

---

# 51. Solicitud válida

Registrar:

```text
Type:
WOUND_CARE

Date:
2026-08-21 09:00

Description:
Cleaning of left front flipper injury.
```

Debe funcionar.

---

# 52. Solicitud inválida 1

Registrar:

```text
Date:
2026-08-15
```

Debe fallar porque:

```text
treatmentDate
<
rescueDate
```

---

# 53. Solicitud inválida 2

Cambiar el caso a:

```text
RELEASED
```

y después intentar registrar:

```text
OBSERVATION
```

Debe producir:

```java
BusinessRuleException
```

---

# 54. Solicitud inválida 3

Desactivar:

```text
SPEC-001
```

e intentar registrar otro tratamiento.

Debe producir:

```java
BusinessRuleException
```

---

# PARTE XII — RETO PARA EL ESTUDIANTE

# 55. Implementar AnimalService

Sin solución proporcionada, crear:

```text
AnimalService
AnimalServiceImpl
```

Debe permitir:

```java
AnimalResponse findByCode(
        String animalCode);
```

y:

```java
List<AnimalResponse>
findAnimalsInRehabilitation();
```

---

# 56. Requisitos

`AnimalResponse` deberá ser un:

```java
record
```

con información como:

```text
id

animalCode

commonName

scientificName

sex

caseCode

rescueStatus
```

Crear además:

```text
AnimalMapper
```

con MapStruct.

---

# 57. Regla adicional

Crear:

```java
boolean canReceiveTreatment(
        String animalCode);
```

Debe retornar:

```text
true
```

cuando el estado sea:

```text
UNDER_EVALUATION

o

IN_REHABILITATION
```

y:

```text
false
```

en los demás casos.

Implementar al menos un unit test.

---

# PARTE XIII — ANÁLISIS

# 58. Clasificar responsabilidades

Indica qué capa debería resolver cada necesidad.

| Necesidad | Capa |
|---|---|
| `SELECT` de RescueCase por código | __________ |
| Validar transición de status | __________ |
| Convertir RescueCase a DTO | __________ |
| Guardar Treatment | __________ |
| Verificar especialista activo | __________ |
| Crear tabla treatments | __________ |
| Controlar transacción | __________ |
| Representar información persistente | __________ |

Opciones:

```text
Entity
Repository
Service
Mapper
Flyway
```

---

# 59. Pregunta crítica

Considere:

```java
if (!specialist.isActive()) {
    throw new BusinessRuleException(...);
}
```

¿Por qué esta lógica NO debería implementarse dentro de:

```java
SpecialistRepository
```

?

---

# 60. Pregunta sobre transacciones

¿Cuál de estas operaciones debería utilizar:

```java
@Transactional(readOnly = true)
```

?

```text
findByCode()

findByStatus()

registerTreatment()

changeStatus()
```

¿Y cuáles requieren una transacción de escritura?

---

# 61. Pregunta sobre Optional

Compara:

```java
RescueCase rescueCase =
        repository
            .findByCaseCode(code)
            .orElseThrow(...);
```

con:

```java
RescueCase rescueCase =
        repository.findByCaseCode(code).get();
```

¿Por qué la segunda alternativa es peligrosa?

---

# 62. Pregunta sobre DTO

¿Por qué no retornar directamente:

```java
RescueCase
```

desde:

```java
RescueCaseService
```

?

Relaciona tu respuesta con:

```text
acoplamiento

Lazy Loading

contrato entre capas

información expuesta

evolución del modelo
```

---

# PARTE XIV — QUÉ NO DEBE HACER UN SERVICE

# 63. Anti-patrón 1

No escribir SQL:

```java
@Service
public class RescueCaseService {

    // SELECT * FROM rescue_cases...
}
```

El acceso a persistencia corresponde al Repository.

---

# 64. Anti-patrón 2

No devolver siempre entidades:

```java
public RescueCase findByCode(...)
```

cuando otras capas solo necesitan una representación específica.

---

# 65. Anti-patrón 3

No convertir el servicio en un simple passthrough:

```java
public RescueCase save(
        RescueCase rescueCase) {

    return repository.save(rescueCase);
}
```

Un Service debería existir porque aporta:

```text
reglas

orquestación

transacciones

abstracción

transformación
```

---

# 66. Anti-patrón 4

Evitar:

```java
repository.findById(id).get();
```

Preferir:

```java
.orElseThrow(...)
```

con un error significativo.

---

# 67. Anti-patrón 5

Evitar field injection:

```java
@Autowired
private AnimalRepository repository;
```

Preferir:

```java
private final AnimalRepository repository;

public AnimalServiceImpl(
        AnimalRepository repository) {

    this.repository = repository;
}
```

---

# PARTE XV — CHECKLIST

# 68. Entrega técnica

El proyecto debe contener:

```text
[ ] DTOs implementados con record

[ ] RescueCaseMapper

[ ] TreatmentMapper

[ ] MapStruct funcionando

[ ] ResourceNotFoundException

[ ] BusinessRuleException

[ ] RescueCaseService

[ ] RescueCaseServiceImpl

[ ] TreatmentService

[ ] TreatmentServiceImpl

[ ] constructor injection

[ ] @Service

[ ] @Transactional

[ ] operaciones readOnly

[ ] Optional + orElseThrow

[ ] reglas de transición

[ ] regla specialist.active

[ ] regla case.status

[ ] validación de fecha de tratamiento

[ ] stream/map/toList

[ ] tests con Mockito

[ ] tests con AssertJ

[ ] test de ResourceNotFoundException

[ ] test de BusinessRuleException

[ ] verify(...)

[ ] verify(..., never())

[ ] AnimalService del reto
```

---

# 69. Tests mínimos

La entrega debe tener al menos:

```text
TEST 1
RescueCase existente
        → retorna DTO

TEST 2
RescueCase inexistente
        → ResourceNotFoundException

TEST 3
Transición de estado válida
        → save()

TEST 4
Transición inválida
        → BusinessRuleException
        → nunca save()

TEST 5
Tratamiento válido
        → save()

TEST 6
Especialista inactivo
        → BusinessRuleException
        → nunca save()

TEST 7
Caso RELEASED
        → BusinessRuleException
```

---

# 70. Ejecutar

Ejecutar:

```bash
mvn clean test
```

El resultado esperado:

```text
BUILD SUCCESS
```

En estos unit tests:

```text
NO debe iniciarse PostgreSQL.

NO debe iniciarse Testcontainers.

NO necesitamos Spring ApplicationContext.
```

Mockito reemplaza los repositories.

---

# 71. Criterios de evaluación

| Criterio | Puntaje |
|---|---:|
| DTOs con `record` | 10 |
| MapStruct | 10 |
| Interfaces Service | 10 |
| Implementaciones Service | 15 |
| Optional y excepciones | 10 |
| Reglas de negocio | 20 |
| Transacciones | 5 |
| Mockito | 10 |
| AssertJ y verificaciones | 5 |
| Reto AnimalService | 5 |
| **Total** | **100** |

---

# 72. Mapa mental final

Al terminar el laboratorio deberías comprender:

```text
                REQUEST
                   │
                   ▼
                 DTO
                   │
                   ▼
              SERVICE
          ┌────────┼─────────┐
          │        │         │
          ▼        ▼         ▼
       reglas   mapper   transaction
          │
          ▼
      REPOSITORY
          │
          ▼
        ENTITY
          │
          ▼
      POSTGRESQL
```

Y para pruebas:

```text
             UNIT TEST
                 │
                 ▼
              SERVICE
             /       \
            /         \
           ▼           ▼
   Repository Mock   Mapper Mock
           │
           X
           │
      No database
```

---

# 73. Competencia final

Después del laboratorio debes ser capaz de recibir un requerimiento como:

> Registrar un tratamiento para el animal AN-001.

y pensar:

```text
1. Recibir DTO

        ↓

2. Buscar Animal

        ↓

3. ¿Existe?

        ↓

4. Buscar Specialist

        ↓

5. ¿Existe?

        ↓

6. ¿Está activo?

        ↓

7. Revisar RescueCase

        ↓

8. ¿Puede recibir tratamientos?

        ↓

9. Validar fecha

        ↓

10. Crear Entity

        ↓

11. Repository.save()

        ↓

12. Mapper

        ↓

13. Response DTO
```

La responsabilidad del Service no es simplemente llamar:

```java
repository.save(...)
```

La capa Service representa el lugar donde la aplicación **interpreta y aplica las reglas del negocio**.