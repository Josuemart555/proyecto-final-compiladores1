# Backend Spring Boot

Base inicial para una API REST en Java 21 con Spring Boot 3.5, pensada para crecer sobre el proyecto de compiladores.

## Qué incluye

- Maven Wrapper (`mvnw`) para no depender de Maven instalado localmente
- Spring Web, Validation y Actuator
- Estructura inicial por responsabilidades: `config`, `system`, `sql`, `shared`
- Validación de entrada con Jakarta Validation
- Manejo global de errores usando `ProblemDetail`
- Configuración por perfiles `dev` y `prod`
- Perfil `local` para logging a archivo fuera de Docker
- Dockerfile multi-stage y `docker-compose.yml`
- Tests de integración y de servicio

## Estructura principal

```text
src/main/java/com/compiladores/api
├── config
├── shared
├── sql
└── system
```

## Ejecutar con Docker

```bash
cd backend
docker compose up --build
```

La API queda disponible en `http://localhost:8080`.

En Docker los logs salen por consola:

```bash
docker compose logs -f
```

## Docker con hot reload

El `docker-compose.yml` ahora está orientado a desarrollo:

- monta el código fuente con `bind mount`
- guarda las dependencias de Maven en el volumen `maven-cache`
- guarda la carpeta `target/` en el volumen `spring-target`
- recompila `src/main/java` y `src/main/resources` dentro del contenedor
- usa `spring-boot-devtools` para reiniciar la app al detectar cambios compilados

Uso normal:

```bash
cd backend
docker compose up --build
```

Después de la primera vez, normalmente basta con:

```bash
docker compose up
```

Si editas clases Java o recursos, el contenedor recompila y Spring Boot reinicia la aplicación sin reconstruir la imagen.

Notas:

- si cambias `pom.xml`, `Dockerfile` o dependencias del sistema, sí necesitas reconstruir con `docker compose up --build`
- `docker compose down` detiene los contenedores pero conserva los volúmenes
- `docker compose down -v` también borra la caché de Maven y `target/`

## Ejecutar tests

Como no tienes Java/Maven instalado localmente, puedes correrlos dentro de Docker:

```bash
cd backend
docker run --rm -v "$(pwd):/app" -w /app maven:3.9.11-eclipse-temurin-21 ./mvnw test
```

## Endpoints iniciales

- `GET /api/v1/system/ping`
- `GET /api/v1/system/info`
- `POST /api/v1/sql/analyze`
- `GET /actuator/health`

## Logging a archivo

Si quieres logs en archivo fuera de Docker, usa el perfil `local`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Ejemplo de análisis SQL

```bash
curl -X POST http://localhost:8080/api/v1/sql/analyze \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT *   FROM usuarios;"}'
```
