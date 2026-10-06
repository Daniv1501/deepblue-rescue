# DeepBlue Rescue — Respuestas del laboratorio de la capa Controlador

## 1. Qué se implementó

| Pieza | Archivo |
|---|---|
| Dependencias `spring-boot-starter-webmvc`, `-validation`, `-webmvc-test` | `pom.xml` |
| Validación de entrada (`@NotNull`, `@NotBlank`, `@PastOrPresent`, `@Size`) | `dto/request/ChangeRescueStatusRequest`, `CreateTreatmentRequest` |
| Contrato de error | `dto/response/ErrorResponse` |
| DTO de elegibilidad | `dto/response/TreatmentEligibilityResponse` |
| Controllers | `controller/RescueCaseController`, `TreatmentController`, `AnimalController` |
| Manejo global de errores | `exception/GlobalExceptionHandler` |
| Tests `@WebMvcTest` | `src/test/.../controller/*ControllerTest` |

Además del contrato del laboratorio se añadió un handler para `NoResourceFoundException` (ruta inexistente → 404 con `ErrorResponse`); sin él, una URL desconocida caería en el handler genérico y devolvería 500.

## 2. Cobertura Service → Controller → Test (sección 105)

| Método Service | Endpoint | Controller | Test |
|---|---|---|---|
| `RescueCaseService.findByCode()` | `GET /api/rescue-cases/{caseCode}` | RescueCaseController | `shouldReturnRescueCaseByCode` |
| `RescueCaseService.findByStatus()` | `GET /api/rescue-cases?status=` | RescueCaseController | `shouldReturnCasesByStatus` |
| `RescueCaseService.changeStatus()` | `PATCH /api/rescue-cases/{caseCode}/status` | RescueCaseController | `shouldChangeStatus` |
| `TreatmentService.register()` | `POST /api/treatments` | TreatmentController | `shouldCreateTreatment` |
| `TreatmentService.findByAnimalCode()` | `GET /api/animals/{animalCode}/treatments` | AnimalController | `shouldReturnAnimalTreatments` |
| `AnimalService.findByCode()` | `GET /api/animals/{animalCode}` | AnimalController | `shouldReturnAnimalByCode` |
| `AnimalService.findAnimalsInRehabilitation()` | `GET /api/animals/in-rehabilitation` | AnimalController | `shouldReturnAnimalsInRehabilitation` |
| `AnimalService.canReceiveTreatment()` | `GET /api/animals/{animalCode}/treatment-eligibility` | AnimalController | `shouldReturnTreatmentEligibility` |

8 métodos Service → 8 operaciones HTTP. Ningún método Service queda sin usar.

## 3. Los 18 casos mínimos de test (sección 107)

| # | Caso | HTTP | Test |
|---|---|---|---|
| 1 | GET RescueCase existente | 200 | `RescueCaseControllerTest.shouldReturnRescueCaseByCode` |
| 2 | GET RescueCase inexistente | 404 | `shouldReturn404WhenCaseDoesNotExist` |
| 3 | GET por status | 200 | `shouldReturnCasesByStatus` |
| 4 | GET status inválido | 400 | `shouldReturn400WhenStatusQueryParamIsInvalid` |
| 5 | PATCH status válido | 200 | `shouldChangeStatus` |
| 6 | PATCH request inválido | 400 + details | `shouldReturn400WhenStatusIsMissing` (+ `...ExplicitlyNull`) |
| 7 | PATCH transición inválida | 409 | `shouldReturn409WhenTransitionIsInvalid` |
| 8 | POST Treatment válido | 201 | `TreatmentControllerTest.shouldCreateTreatment` |
| 9 | POST Treatment inválido | 400 + details | `shouldReturn400WhenRequestIsInvalid` (+ descripción corta, fecha futura) |
| 10 | POST animal inexistente | 404 | `shouldReturn404WhenAnimalDoesNotExist` |
| 11 | POST regla de negocio | 409 | `shouldReturn409WhenBusinessRuleIsViolated` |
| 12 | GET Animal | 200 | `AnimalControllerTest.shouldReturnAnimalByCode` |
| 13 | GET en rehabilitación | 200 | `shouldReturnAnimalsInRehabilitation` |
| 14 | GET tratamientos | 200 | `shouldReturnAnimalTreatments` |
| 15 | GET eligibility | 200 | `shouldReturnTreatmentEligibility` |
| 16 | GET Animal inexistente | 404 | `shouldReturn404WhenAnimalDoesNotExist` |
| 17 | JSON enum inválido | 400 | `RescueCaseControllerTest.shouldReturn400WhenJsonEnumIsInvalid` |
| 18 | Error inesperado | 500 | `RescueCaseControllerTest.shouldReturn500WhenUnexpectedErrorOccurs` |

Reto del estudiante (sección 91) — eligibility: DTO `TreatmentEligibilityResponse`, endpoint en `AnimalController`, llamada a `AnimalService.canReceiveTreatment`, test 200 (`shouldReturnTreatmentEligibility`), test 404 (`shouldReturn404WhenEligibilityAnimalDoesNotExist`) y `verify(...)` en ambos.

## 4. Checkpoints y clasificaciones

### Checkpoint (sección 21)

| Regla | DTO/Controller o Service |
|---|---|
| `animalCode` vacío | DTO/Controller (`@NotBlank`) |
| `status == null` | DTO/Controller (`@NotNull`) |
| animal inexistente | Service |
| especialista inactivo | Service |
| `description` > 500 | DTO/Controller (`@Size`) |
| caso RELEASED | Service |
| transición ADMITTED → RELEASED | Service |

Criterio: si se puede decidir mirando solo el JSON recibido, es validación de entrada; si hace falta consultar datos o el estado del dominio, es regla de negocio.

### Responsabilidades (sección 100)

| Necesidad | Capa |
|---|---|
| Recibir JSON | Controller |
| Verificar `@NotBlank` | DTO Validation |
| Buscar Animal | Repository (orquestado por el Service) |
| Verificar especialista activo | Service |
| Ejecutar query | Repository |
| Convertir excepción en 409 | ControllerAdvice |
| Abrir transacción | Service |
| Retornar 201 | Controller |

### Handlers (secciones 58 y 106)

| Situación | HTTP | Handler |
|---|---|---|
| DTO inválido | 400 | `MethodArgumentNotValidException` |
| JSON inválido | 400 | `HttpMessageNotReadableException` |
| Query param inválido | 400 | `MethodArgumentTypeMismatchException` |
| Recurso inexistente | 404 | `ResourceNotFoundException` |
| Regla de negocio | 409 | `BusinessRuleException` |
| Error inesperado | 500 | `Exception` |

Todos devuelven `ResponseEntity<ErrorResponse>`.

## 5. Reto integrador (secciones 83–90)

Escenario: caso `RES-2026-100` en `IN_REHABILITATION`, animal `AN-2026-100`, especialista `SPEC-001` activo.

| Op. | Request | Resultado |
|---|---|---|
| 1 | `GET /api/rescue-cases/RES-2026-100` | 200 OK |
| 2 | `GET /api/animals/AN-2026-100` | 200 OK |
| 3 | `POST /api/treatments` (WOUND_CARE, 2026-08-21T09:00:00) | 201 Created |
| 4 | `PATCH /api/rescue-cases/RES-2026-100/status` `{"status":"READY_FOR_RELEASE"}` | 200 OK |
| 5 | `PATCH …/status` `{"status": null}` | 400 + `details.status = "Status is required"` |
| 6 | `PATCH …/status` con transición inválida (p. ej. `READY_FOR_RELEASE → ADMITTED`) | 409 Conflict |
| 7 | `GET /api/animals/AN-999` | 404 Not Found |

Cada resultado está cubierto por un test de controller (con el Service simulado). Las reglas que producen el 409 y el 404 viven en el Service y ya están probadas en `RescueCaseServiceImplTest`, `AnimalServiceImplTest` y `TreatmentServiceImplTest`.

## 6. Diseño REST (secciones 92–94)

**92.** `GET /api/animals/AN-001` comunica mejor. El recurso (`animals`) es un sustantivo en la URL y su identificador va en la ruta; el verbo lo da el método HTTP. `/api/getAnimal?id=1` repite el verbo dentro de la URL, mezcla identidad con parámetros y no escala a otros recursos.

**93.** `PATCH /api/rescue-cases/RES-001/status`. Indica que se modifica parcialmente (solo `status`) un recurso identificado. `POST /changeStatus` es una acción RPC: no dice sobre qué recurso actúa y `POST` implica creación.

**94.** `GET /api/animals/AN-001/treatments` expresa pertenencia: los tratamientos *de* ese animal, y es natural para navegar desde el animal. `GET /api/treatments?animal=AN-001` trata a los tratamientos como colección propia que se filtra, y es mejor cuando se quiere combinar filtros (tipo, fechas, especialista). Ambos son válidos; el laboratorio usa el anidado para la consulta por animal y `POST /api/treatments` para crear.

## 7. Anti-patrones (95–99)

- **Controller → Repository:** rompe la separación de capas y esconde reglas fuera del Service. Se evitó: los controllers solo reciben Services.
- **Reglas de negocio en el Controller:** las transiciones de estado, el especialista activo y el caso cerrado viven en el Service.
- **Retornar Entity:** los controllers solo devuelven DTOs de respuesta.
- **try/catch por endpoint:** reemplazado por un único `@RestControllerAdvice`.
- **200 para todo:** se usan 200, 201, 400, 404, 409 y 500 según corresponda.

## 8. Testing por capa (101–104)

- **Repository test** → persistencia (Testcontainers + PostgreSQL real).
- **Service test** → reglas de negocio (Mockito, repositorios simulados).
- **Controller test** → contrato HTTP (`@WebMvcTest` + `MockMvc` + `@MockitoBean`), sin PostgreSQL, sin Repository.

## 9. Preguntas de sustentación (sección 113)

1. **Responsabilidad del Controller:** traducir HTTP hacia la aplicación: URLs, métodos, path/query params, body, validación de entrada, status codes y body de respuesta; luego delega al Service.
2. **Controller vs Service:** el Controller responde "¿cómo llega la petición?"; el Service responde "¿está permitida la operación?" y aplica las reglas de negocio.
3. **Por qué no usar Repository en el Controller:** salta la lógica de negocio y las transacciones, acopla HTTP con persistencia y dificulta las pruebas.
4. **`@RestController`:** combina `@Controller` y `@ResponseBody`; el valor devuelto se serializa a JSON como cuerpo de la respuesta.
5. **`@RequestMapping`:** define la ruta base (y opcionalmente método HTTP) de la clase o del método.
6. **`@PathVariable` vs `@RequestParam`:** el primero toma un segmento de la ruta (`/animals/{code}`) e identifica un recurso; el segundo toma un parámetro de query (`?status=`) y suele filtrar o modificar la consulta.
7. **`@RequestBody`:** deserializa el JSON del cuerpo al DTO de entrada.
8. **`@Valid`:** dispara Bean Validation sobre el objeto; si falla lanza `MethodArgumentNotValidException` antes de entrar al método.
9. **Validación de entrada vs regla de negocio:** la primera se decide solo con los datos recibidos (formato, obligatoriedad, tamaño); la segunda requiere estado del sistema (existencia, estado del caso, especialista activo).
10. **GET:** consultar recursos sin cambiar el estado.
11. **POST:** crear un recurso nuevo.
12. **PATCH:** modificar parcialmente un recurso existente.
13. **200:** la consulta o actualización fue correcta.
14. **201:** se creó un recurso.
15. **400:** el request es inválido (validación, JSON roto, parámetro mal tipado).
16. **404:** el recurso no existe.
17. **409:** la petición es válida pero entra en conflicto con una regla de negocio o el estado del recurso.
18. **500:** error inesperado del servidor.
19. **`ResponseEntity`:** controla explícitamente status, headers y body de la respuesta.
20. **`@RestControllerAdvice`:** centraliza el manejo de excepciones de todos los controllers, evitando `try/catch` repetidos.
21. **`ErrorResponse` común:** el cliente procesa todos los errores igual y la API es predecible.
22. **`details`:** información adicional del error, p. ej. el mensaje por campo en validaciones.
23. **`MethodArgumentNotValidException` vs `BusinessRuleException`:** la primera es un error de formato del request (400, la lanza Spring); la segunda es una regla de dominio violada con un request bien formado (409, la lanza el Service).
24. **`@WebMvcTest`:** levanta solo la capa web (controllers, advice, conversión JSON, validación), no el contexto completo.
25. **Service mock:** el test del Controller verifica el contrato HTTP y la delegación, no la lógica del Service, que ya tiene sus propios tests.
26. **`MockMvc`:** simular peticiones HTTP contra los controllers y verificar status, headers y JSON sin servidor real.
27. **Sin PostgreSQL:** el Service está simulado, así que nunca llega a Repository ni a la base de datos.
28. **`verify(..., never())`:** demuestra que ante un request inválido la validación frena la petición y el Service jamás se invoca.
29. **Transacciones:** la capa Service (`@Transactional`).
30. **Transición de estado:** la capa Service (`RescueCaseServiceImpl.isValidTransition`).

## 10. Observaciones sobre el código existente (no modificado)

- `TreatmentServiceImpl` permite registrar tratamientos en cualquier estado salvo `RELEASED`/`CLOSED`, mientras que `AnimalServiceImpl.canReceiveTreatment` solo da `true` en `UNDER_EVALUATION` e `IN_REHABILITATION`. Por ejemplo, un caso `READY_FOR_RELEASE` aparece como "no elegible" pero `POST /api/treatments` sí lo acepta. Conviene unificar el criterio.
- Los mensajes del Service difieren de los ejemplos del PDF (`"Animal AN-999 does not exist."` en `register`, frente a `"Animal not found: AN-999"`). Los tests de controller no dependen de ello porque simulan el Service.

## 11. Cómo ejecutar

```bash
mvn clean test
```

Los tests de controller no requieren PostgreSQL ni Docker. `PersistenceIntegrationTest` (Testcontainers) sí requiere Docker.
