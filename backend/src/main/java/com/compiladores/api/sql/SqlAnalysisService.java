package com.compiladores.api.sql;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Pattern;

@Service
public class SqlAnalysisService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Clock clock;

    public SqlAnalysisService(Clock clock) {
        this.clock = clock;
    }

    public SqlAnalysisResponse analyze(SqlAnalysisRequest request) {
        String originalSql = request.sql();
        String normalizedSql = normalizeSql(originalSql);
        String trimmedSql = originalSql.trim();

        return new SqlAnalysisResponse(
                normalizedSql,
                trimmedSql.length(),
                trimmedSql.lines().count(),
                countStatements(trimmedSql),
                trimmedSql.endsWith(";"),
                Instant.now(clock)
        );
    }

    private String normalizeSql(String sql) {
        return WHITESPACE.matcher(sql.trim()).replaceAll(" ");
    }

    private long countStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(fragment -> !fragment.isEmpty())
                .count();
    }
}
