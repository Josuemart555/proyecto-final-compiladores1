package com.compiladores.api.shared;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Schema(
        name = "ApiErrorResponse",
        description = "Respuesta de error basada en Problem Detail con campos adicionales de la API."
)
public record ApiErrorResponse(
        @Schema(
                description = "Codigo de estado HTTP.",
                example = "400"
        )
        Integer status,
        @Schema(
                description = "Resumen corto del error.",
                example = "Solicitud invalida"
        )
        String title,
        @Schema(
                description = "Descripcion detallada del error.",
                example = "Uno o mas campos no cumplen las reglas de validacion."
        )
        String detail,
        @Schema(
                description = "Momento en el que se genero el error.",
                example = "2026-04-18T16:45:12.120Z"
        )
        OffsetDateTime timestamp,
        @Schema(
                description = "Errores de validacion agrupados por campo cuando aplica."
        )
        Map<String, List<String>> errors
) {
}
