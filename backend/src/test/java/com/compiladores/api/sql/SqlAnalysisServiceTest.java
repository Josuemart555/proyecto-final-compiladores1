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
    void mysqlIlikeProducesWarning() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT * FROM usuarios WHERE nombre ILIKE '%john%';", "mysql"));
        assertTrue(response.diagnostics().stream()
                .anyMatch(d -> "WARN".equals(d.severity()) && d.message().contains("ILIKE")));
    }

    @Test
    void mysqlCastOperatorProducesWarning() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT id::text FROM facturas;", "mysql"));
        assertTrue(response.diagnostics().stream()
                .anyMatch(d -> "WARN".equals(d.severity()) && d.message().contains("::")));
    }

    @Test
    void postgresqlDialectParsedCorrectly() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest(
                "SELECT p.id, p.nombre FROM productos p WHERE p.precio > 100 LIMIT 20 OFFSET 5;",
                "postgresql"));
        assertTrue(response.valid());
        assertEquals("SELECT", response.statementType());
        assertTrue(response.semantic().tables().contains("p"));
    }
}
