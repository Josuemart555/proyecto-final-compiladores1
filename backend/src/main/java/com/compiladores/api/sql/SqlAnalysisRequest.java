package com.compiladores.api.sql;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Solicitud para analizar y normalizar una sentencia SQL.")
public record SqlAnalysisRequest(
        @Schema(
                description = "Consulta SQL a analizar.",
                example = "SELECT   *  FROM usuarios;  "
        )
        @NotBlank(message = "El campo sql es obligatorio.")
        @Size(max = 5000, message = "El campo sql no puede superar 5000 caracteres.")
        String sql
) {
}
