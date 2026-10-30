# DeepBlue Rescue — Frontend React · Respuestas del laboratorio

Datos de prueba usados (cargados con `datos-prueba.sql`):

| Dato | Valor |
|---|---|
| `caseCode` | `RES-001` (ADMITTED, rescatado el 2026-08-20) |
| `animalCode` | `AN-001` (Green Sea Turtle) |
| `specialistCode` | `SPEC-001` (activo) · `SPEC-002` (inactivo) |

---

## Checkpoint 1 — ¿Por qué el `fetch` usa `/api/...` y no `http://localhost:8080/api/...`?

Porque la página se sirve desde `http://localhost:5173` (Vite). Si el `fetch` fuera a
`http://localhost:8080`, sería una petición a **otro origen** (cambia el puerto), y el navegador la
bloquearía por **CORS**, ya que el backend no envía cabeceras `Access-Control-Allow-Origin`.

Con una ruta relativa `/api/...` la petición va al **mismo origen** (5173). El proxy de
`vite.config.js` la reenvía del lado del servidor a Spring Boot (8080). Entre servidores no hay
CORS, así que el navegador nunca ve otro origen. Además, el código no queda atado a una URL fija:
en producción basta con servir frontend y backend bajo el mismo dominio (o configurar otro
proxy) sin cambiar `api/`.

## Checkpoint 2 — ¿Por qué `request` lanza el error en lugar de devolverlo?

TanStack Query decide el estado según la promesa que devuelve `queryFn` / `mutationFn`. Si la
promesa **se resuelve**, la consulta queda en `isSuccess` y lo resuelto se guarda en `data`. Si
**se rechaza**, queda en `isError` y lo rechazado se guarda en `error`.

`fetch` **no** rechaza la promesa ante un `404` o `409`: solo la rechaza si falla la red. Si
`request` devolviera el `ErrorResponse`, TanStack Query lo trataría como un éxito: `data` sería el
objeto de error, `isError` quedaría en `false` y el `ErrorMessage` nunca se mostraría. En una
mutación, además, se ejecutaría `onSuccess` y se invalidaría la caché como si el cambio se hubiera
aplicado.

Al lanzar un `ApiError` (con `status`, `message` y `details`), el error HTTP se vuelve un
**rechazo**. Así `isError` y `error` funcionan, y los componentes muestran el mensaje del backend.

## Checkpoint 3 — ¿Qué pasaría si `queryKey` fuera solo `['rescue-cases']`?

La `queryKey` identifica la entrada de la caché. Sin `status`, **todos los estados compartirían
una sola entrada**:

- Al cambiar el filtro, la clave no cambia, así que TanStack Query **no hace una consulta
  nueva**. La lista seguiría mostrando los casos `ADMITTED` aunque el `<select>` diga `RELEASED`.
  La `queryFn` sí cambia, porque captura otro `status`, pero TanStack Query no la observa: solo
  observa la clave.
- Si en algún momento se volviera a consultar (por invalidación o recarga), el resultado de un
  estado **sobrescribiría** el del otro y se perdería el beneficio de la caché por estado.

Con `['rescue-cases', status]` cada estado tiene su propia caché. Cambiar el filtro dispara
automáticamente una consulta nueva y volver a un estado ya visto muestra los datos al instante, sin
"Cargando...". Así se observó en *Network*: ir a otro estado y volver a `ADMITTED` no generó una
segunda petición `GET /api/rescue-cases?status=ADMITTED`.

## Checkpoint 4 — ¿Para qué sirve `enabled`? ¿Qué pasaría sin él si `animalCode` fuera `undefined`?

`enabled` indica si la consulta **puede ejecutarse**. Con `enabled: false`, TanStack Query no llama
a `queryFn`: la consulta queda en espera (`isPending`) hasta que la condición se cumpla, y entonces
arranca sola. Sirve para **consultas dependientes**, que necesitan un dato que todavía no existe.

Sin `enabled`, si `animalCode` fuera `undefined` (por ejemplo, un caso sin animal), se harían
tres peticiones inútiles:

```
GET /api/animals/undefined
GET /api/animals/undefined/treatments
GET /api/animals/undefined/treatment-eligibility
```

El backend respondería `404` (animal "undefined" no existe), y la pantalla mostraría errores rojos
que no tienen sentido para el usuario. Además, se guardarían en la caché entradas con la clave
`['animal', undefined]`.

## Checkpoint 5 — ¿Qué hace `invalidateQueries({ queryKey: ['rescue-cases'] })` y por qué afecta a todas las listas?

Marca como **obsoletas** (*stale*) las consultas en caché cuya clave **empieza por**
`['rescue-cases']`. Las que están en pantalla se vuelven a pedir de inmediato. Las demás se
volverán a pedir la próxima vez que se usen.

Afecta a todas las listas porque la comparación es **por prefijo**: `['rescue-cases', 'ADMITTED']`,
`['rescue-cases', 'UNDER_EVALUATION']`, etc. comienzan todas con `['rescue-cases']`. Eso es justo lo
que se necesita: un cambio `ADMITTED → UNDER_EVALUATION` deja desactualizadas **dos** listas, la
de origen (el caso debe desaparecer) y la de destino (el caso debe aparecer). El front no sabe qué
otras listas pueden contener el caso, así que lo seguro es invalidar todas.

**Observación:** `ChangeStatus` solo invalida `['rescue-cases']`. La elegibilidad del animal
(`['animal', code, 'eligibility']`) **no** se refresca al cambiar el estado. Al pasar RES-001 de
`ADMITTED` a `UNDER_EVALUATION`, la tarjeta siguió diciendo "Puede recibir tratamiento: **No**"
(y `rescueStatus: ADMITTED`) hasta que otra acción refrescó el animal. Si además se invalidara
`['animal', caseItem.animalCode]` en el `onSuccess`, el dato quedaría correcto de inmediato.

## Checkpoint 6 — ¿Por qué `useReducer` y no un `useState` por campo? ¿Por qué invalidar `['animal', animalCode]` sin sufijos?

**`useReducer`:**
- El formulario es **un solo objeto de estado** con cuatro campos que cambian juntos (se envían
  juntos y se limpian juntos). Con `useState` harían falta cuatro estados, cuatro setters y cuatro
  `onChange` distintos.
- Un único `handleChange` genérico despacha `{ type: 'field', name, value }` usando el atributo
  `name` del input. Agregar un campo no requiere más código.
- El **reset** es una sola acción (`{ type: 'reset' }` → `initialState`), en lugar de llamar a cuatro
  setters.
- Las transiciones de estado quedan **centralizadas** en una función pura (`formReducer`), fácil de
  leer y de probar sin React.

**`['animal', animalCode]` sin sufijos:**
`invalidateQueries` compara por **prefijo**. Esa clave cubre a la vez:

```
['animal', 'AN-001']                  → datos del animal
['animal', 'AN-001', 'treatments']    → tabla de tratamientos
['animal', 'AN-001', 'eligibility']   → elegibilidad
```

Un tratamiento nuevo afecta la tabla y podría afectar la elegibilidad, así que con una sola llamada
se refresca todo lo de ese animal sin listar cada sufijo. Por eso, en el Paso 4, las tres claves se
diseñaron con el prefijo común. Y no afecta a otros animales (`['animal', 'AN-002', ...]`), porque el
segundo elemento es distinto. En *Network* se observó que, tras registrar, se volvió a pedir
`/treatment-eligibility` sin recargar la página.

---

## Observaciones — Paso 5 (cambiar estado)

| Prueba | Resultado esperado | Resultado observado |
|---|---|---|
| `ADMITTED → UNDER_EVALUATION` (RES-001) | `200`: sale de `ADMITTED` y aparece en `UNDER_EVALUATION` | `200`. RES-001 desapareció de la lista `ADMITTED` (quedó solo RES-002) y apareció en `UNDER_EVALUATION`, sin recargar. La insignia del detalle cambió a `UNDER_EVALUATION`. |
| `ADMITTED → RELEASED` (salto inválido) | `409` en rojo | `Error 409: Invalid status transition for case RES-001: ADMITTED -> RELEASED`. La app siguió funcionando. |

## Observaciones — Paso 6 (registrar tratamiento, animal AN-001)

| Prueba | Resultado esperado | Resultado observado |
|---|---|---|
| Descripción `corta` (< 10) | `400`, `details.description` | `Error 400: Request validation failed` · `description: Description must contain between 10 and 500 characters` |
| `specialistCode` vacío | `400`, `details.specialistCode` | `Error 400: Request validation failed` · `specialistCode: Specialist code is required` |
| Fecha en el futuro (2027-05-01) | `400`, "Treatment date cannot be in the future" | `Error 400: Request validation failed` · `performedAt: Treatment date cannot be in the future` |
| Fecha vacía | `400` ("Required…" o JSON inválido) | `datetime-local` vacío envía `"performedAt": ""`. Jackson suele convertir `""` en `null` para `LocalDateTime`, y entonces se ve `performedAt: Treatment date is required`. Si en cambio rechaza la cadena vacía, se ve `Malformed or invalid JSON request` · `body: Check JSON syntax and enum values`. **Anotar cuál aparece con tu backend.** |
| `specialistCode` = `SP-999` | `404` | `Error 404: Specialist SP-999 does not exist.` |
| Datos válidos (SPEC-001, 2026-08-21 09:00, WOUND_CARE, "Cleaning of left front flipper injury.") | `201`, aparece en la tabla y el form se limpia | "Tratamiento registrado ✔". La fila apareció en la tabla sin recargar, el formulario quedó vacío y se volvió a pedir la elegibilidad (pasó a "Sí", porque el caso ya estaba en `UNDER_EVALUATION`). |

Pruebas adicionales útiles:
- `SPEC-002` (inactivo) → `409 Specialist SPEC-002 is not active.`
- Fecha anterior al rescate (p. ej. 2026-08-15 para RES-001) → `409 Treatment date cannot be before the rescue date (2026-08-20).`
- Un animal de un caso `RELEASED` (AN-005) → elegibilidad "No" y `409` al registrar.

---

## Retos opcionales

- **Reto 1 — Insignias:** hecho. `components/StatusBadge.jsx` + clases `.badge-*` en `index.css`; se
  usa en el detalle del caso.
- Los retos 2 (solo el siguiente estado válido) y 3 (deshabilitar *Registrar* con < 10 caracteres)
  **no** se aplicaron porque impedirían hacer las pruebas obligatorias de las tablas: el salto
  inválido `409` del Paso 5 y la descripción corta `400` del Paso 6.
- Los retos 4 y 5 necesitan paquetes extra (`@tanstack/react-query-devtools`, `react-router-dom`).
