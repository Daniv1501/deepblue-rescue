# Respuestas — Laboratorio DeepBlue Rescue: React y TanStack Query

## Entorno de las pruebas

Las pruebas se ejecutaron en el navegador (Edge) contra el backend Spring Boot real, con estos ajustes:

- Base de datos temporal, con las migraciones del proyecto y datos de prueba creados para este laboratorio.
- Backend en el puerto `8081`, porque Apache ocupa el `8080` en este equipo. El `vite.config.js` entregado apunta a `8080`, como indica la guía.

Datos usados: casos `CASE-001` a `CASE-004`, animales `AN-001` a `AN-004`, especialista activo `SP-001` y especialista inactivo `SP-002`.

---

## Para pensar

### Checkpoint 1: ¿por qué la URL del `fetch` es `/api/...` y no `http://localhost:8080/api/...`?

Una URL relativa se envía al mismo origen de la página (`localhost:5173`), así que el navegador no aplica CORS. El proxy de Vite reenvía la petición a Spring Boot. Con la URL absoluta, la petición iría a otro origen y el navegador la bloquearía si el backend no tuviera CORS configurado. Además, el código no queda atado a una dirección concreta del servidor.

### Checkpoint 2: ¿por qué `request` lanza el error en lugar de devolverlo?

TanStack Query decide `isError` según si la promesa se rechaza. Si el error se devolviera, la promesa se resolvería con éxito: `isError` sería `false` y el error quedaría guardado en `data` como si fueran datos válidos. Por eso hay que comprobar `response.ok`: `fetch` no rechaza la promesa ante un `400`, `404` o `409`.

### Checkpoint 3: ¿qué pasaría si `queryKey` fuera solo `['rescue-cases']`, sin `status`?

Todos los estados compartirían una sola entrada de caché. Al cambiar el filtro, la clave seguiría siendo la misma, así que no se dispararía una consulta nueva y la lista seguiría mostrando los casos del estado anterior bajo el filtro nuevo.

### Checkpoint 4: ¿para qué sirve `enabled`? ¿Qué pasaría sin él si `animalCode` fuera `undefined`?

`enabled` evita que la consulta se ejecute hasta que exista el dato que necesita. Sin él, se harían peticiones a `/api/animals/undefined`, que terminarían en un error `404` innecesario.

### Checkpoint 5: ¿qué hace `invalidateQueries({ queryKey: ['rescue-cases'] })` y por qué afecta a todas las listas?

Marca como desactualizadas todas las consultas cuya clave empieza por ese prefijo, y `['rescue-cases', 'ADMITTED']` y las demás listas lo cumplen. Es necesario porque un cambio de estado saca el caso de una lista y lo mete en otra.

Las consultas en pantalla se vuelven a pedir de inmediato; las demás, cuando se vuelven a usar. En la prueba, tras el `PATCH` solo se repitió `GET ...?status=ADMITTED`. La lista `UNDER_EVALUATION` se actualizó al seleccionarla en el filtro.

### Checkpoint 6: ¿por qué `useReducer` y no un `useState` por campo?

Los campos forman un único formulario que cambia en conjunto. Con `useReducer`, un solo `handleChange` sirve para todos los campos y la acción `reset` limpia el formulario de una vez. Con un `useState` por campo harían falta cuatro estados, cuatro setters y un reset manual de cada uno. Además, la lógica queda reunida en una función pura y fácil de probar.

### Checkpoint 6: ¿por qué `invalidateQueries` usa `['animal', animalCode]` sin más sufijos?

Porque funciona como prefijo: cubre el animal, `['animal', animalCode, 'treatments']` y `['animal', animalCode, 'eligibility']`, y no afecta a otros animales. En la prueba, tras el `201` se repitieron las tres consultas de `AN-002`.

---

## Observaciones de las pruebas

### Paso 5: cambiar el estado

| Prueba | Red | Resultado observado |
|---|---|---|
| `ADMITTED → UNDER_EVALUATION` | `PATCH 200`, luego `GET ?status=ADMITTED 200` | La lista `ADMITTED` mostró "No hay casos con este estado" y `CASE-001` apareció en `UNDER_EVALUATION`, sin recargar la página. |
| `ADMITTED → RELEASED` | `PATCH 409` | Mensaje en rojo: `Error 409: Invalid status transition for case CASE-001: ADMITTED -> RELEASED`. La aplicación siguió funcionando. |

### Paso 6: registrar un tratamiento

| Prueba | Red | Mensaje observado |
|---|---|---|
| Descripción de menos de 10 caracteres | `POST 400` | `description: Description must contain between 10 and 500 characters` |
| `specialistCode` vacío | `POST 400` | `specialistCode: Specialist code is required` |
| Fecha en el futuro | `POST 400` | `performedAt: Treatment date cannot be in the future` |
| Fecha vacía | `POST 400` | `performedAt: Treatment date is required` |
| `specialistCode` = `SP-999` | `POST 404` | `Error 404: Specialist SP-999 does not exist.` |
| Datos válidos | `POST 201`, luego 3 `GET` de `AN-002` | El tratamiento apareció en la tabla sin recargar, el formulario se limpió y se mostró "Tratamiento registrado ✔". |

Dos pruebas adicionales, que no pedía la guía:

| Prueba | Red | Mensaje observado |
|---|---|---|
| Especialista inactivo (`SP-002`) | `POST 409` | `Error 409: Specialist SP-002 is not active.` |
| Fecha anterior al rescate | `POST 409` | `Error 409: Treatment date cannot be before the rescue date (2026-10-05).` |

### Backend detenido (Checkpoint 3)

Se mostró `Error 502: No se pudo contactar el servidor` en rojo, sin pantalla en blanco.

---

## Diferencias con lo que indica la guía

El código entregado es el de la guía, sin cambios. Estas dos conductas se observaron al probarlo.

### 1. La caché no evita que se repita la consulta

Los Checkpoints 3 y 4 indican comprobar en *Network* que la consulta no se repite. Lo observado fue distinto:

- Al volver a un estado ya visitado **no** apareció "Cargando...", porque los datos salen de la caché.
- Sin embargo, *Network* **sí** mostró las consultas otra vez: `GET ?status=ADMITTED` en la lista y las 3 consultas del animal al volver a un caso.

Esto ocurre porque `staleTime` vale `0` por defecto: TanStack Query muestra la caché al instante y actualiza los datos en segundo plano. Para que no se repitan las consultas, habría que definir un `staleTime` mayor en el `QueryClient`.

### 2. El animal queda desactualizado tras cambiar el estado del caso

Después de pasar `CASE-001` a `UNDER_EVALUATION`, la pantalla seguía mostrando `rescueStatus: ADMITTED` y "Puede recibir tratamiento: No", mientras la API ya respondía `eligible: true`.

La causa es que `ChangeStatus` solo invalida `['rescue-cases']`. Se corregiría añadiendo esto en su `onSuccess`:

```js
queryClient.invalidateQueries({ queryKey: ['animal', caseItem.animalCode] })
```
