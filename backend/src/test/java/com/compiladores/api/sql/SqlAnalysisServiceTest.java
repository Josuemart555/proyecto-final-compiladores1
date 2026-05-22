package com.compiladores.api.sql;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAnalysisServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final SqlAnalysisService service = new SqlAnalysisService(clock);

    @Test
    void analyzeNormalizesSqlAndCountsStatements() {
        SqlAnalysisResponse response = service.analyze(new SqlAnalysisRequest("""
                SELECT   *   FROM usuarios;
                DELETE   FROM usuarios WHERE activo = 0;
                """));

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
}
