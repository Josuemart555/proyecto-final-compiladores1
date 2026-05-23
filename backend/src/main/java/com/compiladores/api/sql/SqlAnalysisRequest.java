package com.compiladores.api.sql;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
        name = "SqlAnalysisRequest",
        description = "Solicitud para analizar sintaxis, tokens, AST y reglas por dialecto de una consulta."
)
public record SqlAnalysisRequest(
        @Schema(
                description = """
                        Consulta a analizar.

                        Puede ser SQL para los dialectos mysql, sqlserver o postgresql,
                        o una consulta MongoDB con formato db.<coleccion>.<operacion>(...).
                        """,
                example = "SELECT id, nombre FROM usuarios WHERE activo = 1 LIMIT 10;"
        )
        @NotBlank(message = "El campo sql es obligatorio.")
        @Size(max = 5000, message = "El campo sql no puede superar 5000 caracteres.")
        String sql
        ,
        @Schema(
                description = """
                        Dialecto usado para validar reglas especificas.

                        Valores soportados: mysql, sqlserver, postgresql, mongodb.
                        Si se omite o se envia un valor desconocido, el backend usa mysql por defecto.
                        """,
                allowableValues = {"mysql", "sqlserver", "postgresql", "mongodb"},
                example = "mysql"
        )
        String dialect
) {
    public SqlAnalysisRequest(String sql) {
        this(sql, null);
    }
}
