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
}
