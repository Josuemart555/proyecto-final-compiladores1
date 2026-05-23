# Proyecto Final Compiladores 1

Aplicacion web para validar consultas de distintos lenguajes de consulta:

- MySQL
- SQL Server
- PostgreSQL
- MongoDB

El proyecto esta dividido en dos aplicaciones principales:

- `backend/`: API REST en Java 21 con Spring Boot.
- `frontend/`: interfaz web en React + Vite.

Tambien existe una carpeta `c++/` con una version/implementacion auxiliar del compilador.

## Requisitos

Para levantar el proyecto en otra computadora, la forma recomendada es usar Docker.

### Obligatorio

Instala:

- Git
- Docker Desktop, o Docker Engine con Docker Compose

Versiones recomendadas:

- Docker 24 o superior
- Docker Compose v2

Verifica la instalacion:

```bash
git --version
docker --version
docker compose version
```

En Windows se recomienda usar Docker Desktop con WSL2 habilitado.

### Opcional para desarrollo sin Docker

Solo necesitas esto si quieres ejecutar backend/frontend directamente en tu maquina:

- Java 21
- Node.js 22 o superior
- npm

No es obligatorio instalar Maven, porque el backend incluye Maven Wrapper (`mvnw`).

## Puertos usados

Por defecto el proyecto usa:

| Servicio | URL | Puerto |
| --- | --- | --- |
| Frontend | `http://localhost:5173` | `5173` |
| Backend API | `http://localhost:8080` | `8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` | `8080` |
| Health check | `http://localhost:8080/actuator/health` | `8080` |

Si otro programa ya usa esos puertos, revisa la seccion de configuracion de puertos.

## Levantar el proyecto con Docker

Clona el repositorio:

```bash
git clone <URL_DEL_REPOSITORIO>
cd proyecto-final-compiladores1
```

### 1. Levantar el backend

En una terminal:

```bash
cd backend
docker compose up --build
```

Cuando termine de iniciar, valida que este funcionando:

```bash
curl http://localhost:8080/actuator/health
```

Respuesta esperada:

```json
{"status":"UP"}
```

### 2. Levantar el frontend

En otra terminal:

```bash
cd frontend
docker compose up --build
```

Abre la aplicacion en:

```text
http://localhost:5173
```

Desde ahi puedes escribir consultas, seleccionar el dialecto y presionar `Run Query`.

## Uso rapido

1. Abre `http://localhost:5173`.
2. Selecciona el dialecto: MySQL, SQL Server, PostgreSQL o MongoDB.
3. Escribe una consulta.
4. Presiona `Run Query`.
5. La aplicacion mostrara:
   - `Compilation Successful` si la sintaxis es valida.
   - `Compilation Failed` si hay errores.
   - Detalle de fase, mensaje, linea y columna del error.

Ejemplo valido para MySQL:

```sql
SELECT id, nombre, email
FROM clientes
WHERE estado = 'activo'
ORDER BY nombre;
```

Ejemplo invalido en MySQL, pero valido en SQL Server:

```sql
SELECT TOP 10 * FROM usuarios;
```

## API del backend

Endpoint principal:

```http
POST /api/v1/sql/analyze
```

URL completa:

```text
http://localhost:8080/api/v1/sql/analyze
```

Ejemplo con `curl`:

```bash
curl -X POST http://localhost:8080/api/v1/sql/analyze \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT * FROM usuarios LIMIT 10;","dialect":"mysql"}'
```

Dialectos soportados:

```text
mysql
sqlserver
postgresql
mongodb
```

La API valida sintaxis y estructura. No ejecuta consultas contra una base de datos real.

## Validaciones soportadas

El backend valida reglas comunes de SQL y reglas especificas por dialecto.

### MySQL

Soporta consultas como:

- `SELECT`, `WHERE`, `ORDER BY`
- `JOIN`
- `GROUP BY`, `HAVING`
- `LIMIT`
- CTE con `WITH`
- funciones agregadas
- funciones de ventana como `RANK() OVER (...)`
- identificadores con backticks: `` `nombre` ``

Marca error en sintaxis propia de otros motores, por ejemplo:

- `TOP`
- `ILIKE`
- operador PostgreSQL `::`
- `RETURNING`

### SQL Server

Soporta consultas como:

- `SELECT TOP`
- identificadores con corchetes: `[Nombre]`
- `ORDER BY ... OFFSET ... ROWS`
- `MERGE INTO ... USING ... WHEN MATCHED`
- CTE recursivo con `WITH ... UNION ALL`

Marca error en sintaxis no compatible, por ejemplo:

- `LIMIT`
- `ILIKE`
- operador PostgreSQL `::`

### PostgreSQL

Soporta consultas como:

- `ILIKE`
- `LIMIT` y `OFFSET`
- parametros posicionales: `$1`, `$2`
- identificadores con comillas dobles: `"nombre"`
- operadores JSONB como `->>`
- `CAST(...)`
- funciones de ventana con frame clauses
- operador de cast `::`

Marca error en sintaxis no compatible, por ejemplo:

- `TOP`
- identificadores MySQL con backticks

### MongoDB

Soporta consultas como:

- `db.coleccion.find(...)`
- `db.coleccion.count(...)`
- `db.coleccion.aggregate([...])`
- filtros y proyecciones
- pipelines con `$match`, `$group`, `$sort`, `$unwind`, `$lookup`, `$sum`, `$avg`, `$multiply`
- comentarios `//` dentro de pipelines

Marca error cuando:

- la consulta no inicia con `db.<coleccion>.<operacion>(...)`
- la operacion no esta soportada
- hay llaves, corchetes o parentesis sin cerrar
- hay contenido despues del parentesis final

## Comandos utiles con Docker

Ver contenedores activos:

```bash
docker ps
```

Ver logs del backend:

```bash
cd backend
docker compose logs -f
```

Ver logs del frontend:

```bash
cd frontend
docker compose logs -f
```

Detener backend:

```bash
cd backend
docker compose down
```

Detener frontend:

```bash
cd frontend
docker compose down
```

Reconstruir imagenes:

```bash
cd backend
docker compose up --build
```

```bash
cd frontend
docker compose up --build
```

Limpiar volumenes de desarrollo:

```bash
cd backend
docker compose down -v
```

```bash
cd frontend
docker compose down -v
```

## Ejecutar pruebas

Con el backend ya levantado:

```bash
docker exec compilador-sql-api ./mvnw test
```

Tambien puedes correrlas sin tener el backend levantado usando una imagen Maven:

```bash
cd backend
docker run --rm -v "$(pwd):/app" -w /app maven:3.9.11-eclipse-temurin-21 ./mvnw test
```

En Windows PowerShell:

```powershell
cd backend
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.11-eclipse-temurin-21 ./mvnw test
```

## Compilar frontend

Con el contenedor del frontend activo:

```bash
docker exec logic-atelier-frontend npm run build
```

Sin Docker, si tienes Node.js instalado:

```bash
cd frontend
npm install
npm run build
```

## Ejecucion local sin Docker

Esta opcion es para desarrollo avanzado.

### Backend local

Requisitos:

- Java 21

Comando:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En Windows PowerShell:

```powershell
cd backend
.\mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend local

Requisitos:

- Node.js 22 o superior
- npm

Comandos:

```bash
cd frontend
npm install
npm run dev
```

Si el backend usa otra URL:

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

En Windows PowerShell:

```powershell
$env:VITE_API_BASE_URL="http://localhost:8080"
npm run dev
```

## Configuracion de puertos

### Cambiar puerto del backend

```bash
cd backend
APP_PORT=8081 docker compose up --build
```

En ese caso el backend quedara en:

```text
http://localhost:8081
```

Tambien debes apuntar el frontend a ese puerto:

```bash
cd frontend
VITE_API_BASE_URL=http://localhost:8081 docker compose up --build
```

### Cambiar puerto del frontend

```bash
cd frontend
FRONTEND_PORT=5174 docker compose up --build
```

La app quedara en:

```text
http://localhost:5174
```

Importante: si cambias el puerto del frontend, el backend debe permitir ese origen en la configuracion CORS.

## Solucion de problemas

### El frontend muestra error de conexion con el backend

Revisa que el backend este arriba:

```bash
docker ps
curl http://localhost:8080/actuator/health
```

Tambien revisa que el frontend este usando la URL correcta:

```bash
cd frontend
docker compose logs -f
```

Por defecto debe usar:

```text
http://localhost:8080
```

### Error CORS

El frontend por defecto corre en:

```text
http://localhost:5173
```

Ese origen esta permitido por el backend. Si cambias el puerto del frontend, debes actualizar la configuracion CORS en:

```text
backend/src/main/java/com/compiladores/api/config/CorsConfig.java
```

### El puerto 8080 o 5173 ya esta ocupado

Busca que proceso usa el puerto:

```bash
docker ps
```

Puedes cambiar los puertos con `APP_PORT` y `FRONTEND_PORT`, como se indica en la seccion de configuracion de puertos.

### Permiso denegado en `target/` o `dist/`

Puede pasar si Docker creo archivos como `root` y luego intentas compilar localmente.

Soluciones recomendadas:

1. Ejecuta los comandos dentro del contenedor:

```bash
docker exec compilador-sql-api ./mvnw test
docker exec logic-atelier-frontend npm run build
```

2. O elimina los volumenes/contenedores de desarrollo:

```bash
cd backend
docker compose down -v
```

```bash
cd frontend
docker compose down -v
```

### Docker no encuentra `docker compose`

Si `docker compose version` falla, probablemente tienes una instalacion vieja que usa `docker-compose`.

Prueba:

```bash
docker-compose --version
```

Si funciona, puedes reemplazar `docker compose` por `docker-compose` en los comandos.

## Estructura del proyecto

```text
proyecto-final-compiladores1/
├── backend/
│   ├── src/main/java/com/compiladores/api/
│   ├── src/test/java/com/compiladores/api/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── README.md
├── frontend/
│   ├── src/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── README.md
├── c++/
│   └── README.md
└── README.md
```

## Notas importantes

- El backend valida sintaxis, tokens, reglas por dialecto y estructura basica.
- El backend no se conecta a MySQL, SQL Server, PostgreSQL ni MongoDB reales.
- El frontend muestra los mensajes enviados por el backend cuando hay errores.
- Para desarrollo normal se recomienda levantar backend y frontend con Docker en terminales separadas.
