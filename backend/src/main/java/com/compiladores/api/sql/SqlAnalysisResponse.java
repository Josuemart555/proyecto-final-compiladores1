package com.compiladores.api.sql;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(
        name = "SqlAnalysisResponse",
        description = "Resultado completo del analisis lexico, sintactico y semantico de una consulta."
)
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
        Instant analyzedAt,
        @Schema(
                description = "Indica si la consulta paso todas las validaciones. Si es false, revisar diagnostics.",
                example = "true"
        )
        boolean valid,
        @Schema(
                description = "Tipo de sentencia detectada como raiz del analisis.",
                example = "SELECT"
        )
        String statementType,
        @Schema(description = "Lista de tokens detectados por el lexer, en orden de aparicion.")
        List<SqlToken> tokens,
        @Schema(description = "Arbol de sintaxis abstracta generado por el parser.")
        AstNode ast,
        @Schema(description = "Errores y advertencias encontrados durante el analisis.")
        List<SqlDiagnostic> diagnostics,
        @Schema(description = "Reporte semantico con tablas, columnas y advertencias de identificadores.")
        SemanticReport semantic,
        @Schema(description = "Reporte de ejecucion. Actualmente la API valida sintaxis y no ejecuta contra bases reales.")
        ExecutionReport execution
) {
    @Schema(name = "SqlToken", description = "Token individual detectado por el lexer.")
    public record SqlToken(
            @Schema(description = "Tipo interno del token.", example = "KEYWORD")
            String type,
            @Schema(description = "Texto original o normalizado del token.", example = "SELECT")
            String lexeme,
            @Schema(description = "Linea donde inicia el token.", example = "1")
            int line,
            @Schema(description = "Columna donde inicia el token.", example = "1")
            int column
    ) {
    }

    @Schema(name = "AstNode", description = "Nodo del arbol de sintaxis abstracta.")
    public record AstNode(
            @Schema(description = "Tipo del nodo.", example = "Clause")
            String type,
            @Schema(description = "Etiqueta visible del nodo.", example = "SELECT")
            String label,
            @Schema(description = "Valor asociado al nodo cuando aplica.", example = "SELECT")
            String value,
            @Schema(description = "Nodos hijos.")
            List<AstNode> children
    ) {
    }

    @Schema(name = "SqlDiagnostic", description = "Mensaje generado por el lexer, parser o validador del dialecto.")
    public record SqlDiagnostic(
            @Schema(description = "Fase del analisis que genero el diagnostico.", example = "PARSER")
            String phase,
            @Schema(description = "Severidad del diagnostico.", allowableValues = {"ERROR", "WARN"}, example = "ERROR")
            String severity,
            @Schema(description = "Mensaje legible para el usuario.", example = "La clausula TOP no es compatible con MySQL. Use LIMIT.")
            String message,
            @Schema(description = "Linea aproximada del problema.", example = "1")
            int line,
            @Schema(description = "Columna aproximada del problema.", example = "8")
            int column
    ) {
    }

    @Schema(name = "SemanticReport", description = "Resumen de simbolos detectados durante el analisis.")
    public record SemanticReport(
            @Schema(description = "Indica si el modulo semantico pudo construir el reporte.", example = "true")
            boolean connected,
            @Schema(description = "Tablas o colecciones detectadas.", example = "[\"usuarios\"]")
            List<String> tables,
            @Schema(description = "Columnas, campos o expresiones detectadas.", example = "[\"id\", \"nombre\"]")
            List<String> columns,
            @Schema(description = "Advertencias semanticas no bloqueantes.", example = "[\"Columna calificada detectada: u.id\"]")
            List<String> warnings
    ) {
    }

    @Schema(name = "ExecutionReport", description = "Estado de ejecucion simulada o mensaje operacional del analizador.")
    public record ExecutionReport(
            @Schema(description = "Indica si el backend ejecuto una simulacion de resultados. No implica ejecucion en DB real.", example = "false")
            boolean executed,
            @Schema(description = "Mensaje de ejecucion o razon por la que no se ejecuto.", example = "Sin ejecucion: este servicio opera en modo analisis sin conexion a base de datos.")
            String message,
            @Schema(description = "Tiempo reportado para ejecucion simulada.", example = "0")
            long elapsedMs,
            @Schema(description = "Cantidad de filas simuladas o devueltas.", example = "0")
            int rowCount,
            @Schema(description = "Columnas del resultado simulado.")
            List<String> columns,
            @Schema(description = "Filas del resultado simulado.")
            List<Map<String, Object>> rows
    ) {
    }
}
