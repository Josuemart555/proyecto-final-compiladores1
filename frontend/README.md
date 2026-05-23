# Logic Atelier Frontend

Frontend React para ejecutar y visualizar consultas SQL usando el backend Spring Boot.

## Ejecutar con Docker

```bash
cd frontend
docker compose up --build
```

La app queda en `http://localhost:5173` y consume la API en `http://localhost:8080`.

Si el backend corre en otro host:

```bash
VITE_API_BASE_URL=http://localhost:8080 docker compose up --build
```
