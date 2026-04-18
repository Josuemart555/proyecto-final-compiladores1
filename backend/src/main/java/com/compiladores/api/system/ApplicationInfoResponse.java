package com.compiladores.api.system;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Metadatos generales de la aplicacion.")
public record ApplicationInfoResponse(
        @Schema(
                description = "Nombre visible de la aplicacion.",
                example = "Compilador SQL API"
        )
        String name,
        @Schema(
                description = "Descripcion funcional de la aplicacion.",
                example = "API REST base para el proyecto final de compiladores"
        )
        String description,
        @Schema(
                description = "Version publicada de la aplicacion.",
                example = "0.0.1-SNAPSHOT"
        )
        String version,
        @Schema(
                description = "Perfil activo de Spring.",
                example = "default"
        )
        String profile,
        @Schema(
                description = "Momento en el que se consulto la informacion.",
                example = "2026-04-18T16:45:12.120Z"
        )
        Instant timestamp
) {
}
