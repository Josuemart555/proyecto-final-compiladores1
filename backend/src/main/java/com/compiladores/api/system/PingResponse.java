package com.compiladores.api.system;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estado basico de disponibilidad del servicio.")
public record PingResponse(
        @Schema(
                description = "Estado del servicio.",
                example = "UP"
        )
        String status,
        @Schema(
                description = "Momento en el que se genero la respuesta.",
                example = "2026-04-18T16:45:12.120Z"
        )
        Instant timestamp
) {
}
