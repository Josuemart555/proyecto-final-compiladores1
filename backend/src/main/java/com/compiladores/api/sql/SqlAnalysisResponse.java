package com.compiladores.api.sql;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resultado del analisis basico de una consulta SQL.")
public record SqlAnalysisResponse(
        @Schema(
                description = "Consulta SQL normalizada, con espacios redundantes eliminados.",
                example = "SELECT * FROM usuarios;"
        )
        String normalizedSql,
        @Schema(
                description = "Cantidad de caracteres de la consulta tras aplicar trim.",
                example = "23"
        )
        int characterCount,
        @Schema(
                description = "Cantidad de lineas detectadas en la consulta.",
                example = "1"
        )
        long lineCount,
        @Schema(
                description = "Numero de sentencias SQL separadas por punto y coma.",
                example = "1"
        )
        long statementCount,
        @Schema(
                description = "Indica si la consulta termina con punto y coma.",
                example = "true"
        )
        boolean trailingSemicolon,
        @Schema(
                description = "Momento en el que se realizo el analisis.",
                example = "2026-04-18T16:45:12.120Z"
        )
        Instant analyzedAt
) {
}
