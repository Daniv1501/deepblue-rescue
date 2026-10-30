# DeepBlue Rescue — UI (React + TanStack Query)

Panel que consume el API REST de DeepBlue Rescue.

## Requisitos
- Node 20+ y npm
- Backend DeepBlue corriendo en `http://localhost:8080` con datos

## Cargar datos de prueba (una vez)
Con el backend ya iniciado al menos una vez (Flyway crea las tablas):

```bash
psql -h localhost -U postgres -d deepblue -f datos-prueba.sql
```

## Ejecutar

```bash
npm install
npm run dev
```

Abrir http://localhost:5173. Vite reenvía `/api` al backend (ver `vite.config.js`).

Las respuestas de los "Para pensar" y las observaciones de las pruebas están en `RESPUESTAS.md`.
