package com.compiladores.api.sql;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalysisServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final SqlAnalysisService service = new SqlAnalysisService(clock, 100);

    @Test
    void analyzeNormalizesSqlAndCountsStatements() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest("""
                SELECT   *   FROM usuarios;
                DELETE   FROM usuarios WHERE activo = 0;
                """, "mysql"));

        assertEquals("SELECT * FROM usuarios; DELETE FROM usuarios WHERE activo = 0;", response.normalizedSql());
        assertEquals(2, response.statementCount());
        assertEquals(2, response.lineCount());
        assertTrue(response.trailingSemicolon());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), response.analyzedAt());
    }

    @Test
    void analyzeMongoDbQueryValidatesSyntaxOnly() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest("db.presupuestos.find({ status: \"A\" });", "mongodb"));

        assertEquals("db.presupuestos.find({ status: \"A\" });", response.normalizedSql());
        assertEquals("FIND", response.statementType());
        assertTrue(response.valid());
        assertEquals(1, response.semantic().tables().size());
        assertEquals("presupuestos", response.semantic().tables().get(0));
        assertEquals(false, response.execution().executed());
        assertEquals("Consulta MongoDB válida. Solo se valida sintaxis y estructura; no hay conexión a base de datos.", response.execution().message());
        assertEquals(0, response.execution().rowCount());
        assertTrue(response.execution().columns().isEmpty());
        assertTrue(response.execution().rows().isEmpty());
    }

    @Test
    void analyzeMongoDbQueryWithCorreoFieldValidatesSyntaxOnly() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest("db.usuarios.find({ correo: \"juan@ejemplo.com\" })", "mongodb"));

        assertEquals("db.usuarios.find({ correo: \"juan@ejemplo.com\" })", response.normalizedSql());
        assertEquals("FIND", response.statementType());
        assertTrue(response.valid());
        assertEquals(false, response.execution().executed());
        assertEquals("Consulta MongoDB válida. Solo se valida sintaxis y estructura; no hay conexión a base de datos.", response.execution().message());
        assertEquals(0, response.execution().rowCount());
        assertTrue(response.execution().columns().isEmpty());
        assertTrue(response.execution().rows().isEmpty());
    }

    @Test
    void mongodbAggregateWithArrayPipelineIsValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "db.ventas.aggregate([{ $match: { estado: 'PAGADO' } }])", "mongodb"));

        assertTrue(response.valid());
        assertEquals("AGGREGATE", response.statementType());
        assertTrue(response.semantic().tables().contains("ventas"));
    }

    @Test
    void mongodbRejectsUnsupportedWriteOperation() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "db.usuarios.insertOne({ nombre: 'Ana' })", "mongodb"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "no soportada");
    }

    @Test
    void mongodbRejectsTrailingContentAfterClosingParenthesis() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "db.usuarios.find({ activo: true }) basura", "mongodb"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "despues del parentesis");
    }

    @Test
    void mysqlBacktickIdentifiersAndLimitAreValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT `nombre`, `total` FROM `presupuestos` LIMIT 10;", "mysql"));

        assertTrue(response.valid());
        assertTrue(response.semantic().columns().contains("nombre"));
        assertTrue(response.semantic().tables().contains("presupuestos"));
    }

    @Test
    void mysqlRejectsSqlServerTop() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT TOP 10 * FROM usuarios;", "mysql"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "TOP");
    }

    @Test
    void mysqlProvidedEasyQueryIsValid() {
        assertValid("mysql", """
                SELECT id, nombre, email, fecha_registro
                FROM clientes
                WHERE estado = 'activo'
                  AND fecha_registro >= '2026-01-01'
                ORDER BY fecha_registro DESC;
                """);
    }

    @Test
    void mysqlProvidedMediumJoinAggregationQueryIsValid() {
        assertValid("mysql", """
                SELECT c.nombre, SUM(p.total) AS total_gastado, COUNT(p.id) AS cantidad_pedidos
                FROM clientes c
                INNER JOIN pedidos p ON c.id = p.cliente_id
                WHERE p.estado = 'completado'
                GROUP BY c.id, c.nombre
                HAVING total_gastado > 500;
                """);
    }

    @Test
    void mysqlProvidedComplexCteWindowQueryIsValid() {
        assertValid("mysql", """
                WITH ComprasClasificadas AS (
                    SELECT
                        cliente_id,
                        monto,
                        fecha,
                        RANK() OVER (PARTITION BY cliente_id ORDER BY monto DESC) as ranking
                    FROM compras
                )
                SELECT cliente_id, monto, fecha, ranking
                FROM ComprasClasificadas
                WHERE ranking <= 3;
                """);
    }

    @Test
    void sqlServerTopAndBracketIdentifiersAreValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT TOP 10 [nombre] FROM [usuarios] ORDER BY [id] OFFSET 0 ROWS;", "sqlserver"));

        assertTrue(response.valid());
        assertTrue(response.semantic().columns().contains("TOP 10 nombre"));
        assertTrue(response.semantic().tables().contains("usuarios"));
    }

    @Test
    void sqlServerRejectsLimit() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios LIMIT 10;", "sqlserver"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "LIMIT");
    }

    @Test
    void sqlServerRejectsOffsetWithoutOrderBy() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios OFFSET 10 ROWS;", "sqlserver"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "ORDER BY");
    }

    @Test
    void sqlServerProvidedEasyTopQueryIsValid() {
        assertValid("sqlserver", """
                SELECT TOP 10 ProductoID, Nombre, StockActual
                FROM Inventario
                WHERE StockActual < NivelReorden
                ORDER BY StockActual ASC;
                """);
    }

    @Test
    void sqlServerProvidedMergeQueryIsValid() {
        assertValid("sqlserver", """
                MERGE INTO Inventario AS target
                USING ProductosNuevos AS source
                ON target.ProductoID = source.ProductoID
                WHEN MATCHED THEN
                    UPDATE SET target.StockActual = target.StockActual + source.Cantidad
                WHEN NOT MATCHED THEN
                    INSERT (ProductoID, Nombre, StockActual)
                    VALUES (source.ProductoID, source.Nombre, source.Cantidad);
                """);
    }

    @Test
    void sqlServerProvidedRecursiveCteQueryIsValid() {
        assertValid("sqlserver", """
                WITH JerarquiaEmpleados AS (
                    SELECT EmpleadoID, Nombre, ManagerID, 1 AS NivelJerarquia
                    FROM Empleados
                    WHERE ManagerID IS NULL

                    UNION ALL

                    SELECT e.EmpleadoID, e.Nombre, e.ManagerID, j.NivelJerarquia + 1
                    FROM Empleados e
                    INNER JOIN JerarquiaEmpleados j ON e.ManagerID = j.EmpleadoID
                )
                SELECT EmpleadoID, Nombre, NivelJerarquia
                FROM JerarquiaEmpleados
                ORDER BY NivelJerarquia, Nombre;
                """);
    }

    @Test
    void postgresqlLimitIsValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios WHERE activo = 1 LIMIT 10;", "postgresql"));
        assertTrue(response.valid());
        assertTrue(response.diagnostics().stream()
                .noneMatch(d -> "ERROR".equals(d.severity()) && d.message().contains("LIMIT")));
    }

    @Test
    void postgresqlTopIsInvalid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT TOP 10 * FROM usuarios;", "postgresql"));
        assertFalse(response.valid());
        assertTrue(response.diagnostics().stream()
                .anyMatch(d -> "ERROR".equals(d.severity()) && d.message().contains("TOP")));
    }

    @Test
    void postgresqlDoubleQuoteIdentifiers() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT \"nombre\", \"apellido\" FROM \"usuarios\";", "postgresql"));
        assertTrue(response.valid());
        assertTrue(response.semantic().columns().contains("nombre"));
        assertTrue(response.semantic().columns().contains("apellido"));
        assertTrue(response.semantic().tables().contains("usuarios"));
    }

    @Test
    void postgresqlIlikeIsValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios WHERE nombre ILIKE '%john%';", "postgresql"));
        assertTrue(response.valid());
        assertTrue(response.diagnostics().stream()
                .noneMatch(d -> "WARN".equals(d.severity()) && d.message().contains("ILIKE")));
    }

    @Test
    void postgresqlCastOperatorIsValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT id::text, monto::numeric FROM facturas;", "postgresql"));
        assertTrue(response.valid());
        assertTrue(response.diagnostics().stream()
                .noneMatch(d -> d.message().contains("::")));
    }

    @Test
    void postgresqlPositionalParamsAreValid() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios WHERE id = $1 AND activo = $2;", "postgresql"));
        assertTrue(response.valid());
    }

    @Test
    void postgresqlRejectsMysqlBacktickIdentifier() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT `nombre` FROM usuarios;", "postgresql"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "Token no reconocido");
    }

    @Test
    void mysqlIlikeProducesSyntaxError() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios WHERE nombre ILIKE '%john%';", "mysql"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "ILIKE");
    }

    @Test
    void mysqlCastOperatorProducesSyntaxError() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT id::text FROM facturas;", "mysql"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "::");
    }

    @Test
    void postgresqlProvidedEasyIlikeQueryIsValid() {
        assertValid("postgresql", """
                SELECT id, nombre, email
                FROM usuarios
                WHERE email ILIKE '%gmail.com%';
                """);
    }

    @Test
    void postgresqlProvidedJsonbQueryIsValid() {
        assertValid("postgresql", """
                SELECT
                    id,
                    nombre,
                    configuracion->>'tema_ui' AS tema_preferido
                FROM cuentas
                WHERE configuracion->>'notificaciones_activas' = 'true'
                  AND CAST(configuracion->>'limite_almacenamiento' AS INTEGER) > 100;
                """);
    }

    @Test
    void postgresqlProvidedWindowFrameQueryIsValid() {
        assertValid("postgresql", """
                SELECT
                    fecha_venta,
                    total_dia,
                    ROUND(
                        AVG(total_dia) OVER (
                            ORDER BY fecha_venta
                            ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                        ), 2
                    ) AS promedio_movil_7_dias
                FROM ventas_diarias;
                """);
    }

    @Test
    void postgresqlDialectParsedCorrectly() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT p.id, p.nombre FROM productos p WHERE p.precio > 100 LIMIT 20 OFFSET 5;",
                "postgresql"));
        assertTrue(response.valid());
        assertEquals("SELECT", response.statementType());
        assertTrue(response.semantic().tables().contains("productos"));
    }

    @Test
    void selectRequiresExpression() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest("SELECT FROM usuarios;", "mysql"));

        assertFalse(response.valid());
        assertHasDiagnostic(response, "ERROR", "SELECT requiere");
    }

    @Test
    void mongodbProvidedFindProjectionQueryIsValid() {
        assertValid("mongodb", """
                db.usuarios.find(
                  { pais: "GT", estado: "activo" },
                  { _id: 0, nombre: 1, edad: 1 }
                );
                """);
    }

    @Test
    void mongodbProvidedAggregationGroupQueryIsValid() {
        assertValid("mongodb", """
                db.usuarios.aggregate([
                  { $match: { edad: { $gte: 18 } } },
                  { $group: {
                      _id: "$ciudad",
                      totalUsuarios: { $sum: 1 },
                      edadPromedio: { $avg: "$edad" }
                  }},
                  { $sort: { totalUsuarios: -1 } }
                ]);
                """);
    }

    @Test
    void mongodbProvidedComplexPipelineQueryIsValid() {
        assertValid("mongodb", """
                db.pedidos.aggregate([
                  // 1. Filtrar pedidos completados
                  { $match: { estado: "completado" } },

                  // 2. Desestructurar el array de items (crea un documento por cada item del array)
                  { $unwind: "$items" },

                  // 3. Hacer "JOIN" con la colección de productos
                  { $lookup: {
                      from: "productos",
                      localField: "items.producto_id",
                      foreignField: "_id",
                      as: "producto_detalle"
                  }},

                  // 4. El lookup devuelve un array, lo desempaquetamos
                  { $unwind: "$producto_detalle" },

                  // 5. Agrupar por categoría de producto y calcular totales matemáticos
                  { $group: {
                      _id: "$producto_detalle.categoria",
                      cantidadVendida: { $sum: "$items.cantidad" },
                      ingresosTotales: {
                          $sum: { $multiply: ["$items.cantidad", "$producto_detalle.precio"] }
                      }
                  }},

                  // 6. Ordenar por las categorías que generaron más ingresos
                  { $sort: { ingresosTotales: -1 } }
                ]);
                """);
    }

    private void assertValid(String dialect, String sql) {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(sql, dialect));
        assertTrue(response.valid(), () -> "Diagnostics: " + response.diagnostics());
    }

    private void assertHasDiagnostic(SqlAnalysisResponse response, String severity, String messagePart) {
        assertTrue(response.diagnostics().stream()
                .anyMatch(d -> severity.equals(d.severity()) && d.message().contains(messagePart)));
    }
}
