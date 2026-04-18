package com.compiladores.api.system;

import com.compiladores.api.shared.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@Tag(name = "System", description = "Operaciones de salud y metadatos de la aplicacion.")
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final ApplicationInfoService applicationInfoService;
    private final Clock clock;

    public SystemController(ApplicationInfoService applicationInfoService, Clock clock) {
        this.applicationInfoService = applicationInfoService;
        this.clock = clock;
    }

    @Operation(
            summary = "Consultar estado del servicio",
            description = "Devuelve una respuesta simple para verificar que la API se encuentra disponible."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Servicio disponible"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno inesperado",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping("/ping")
    public ResponseEntity<PingResponse> ping() {
        return ResponseEntity.ok(new PingResponse("UP", Instant.now(clock)));
    }

    @Operation(
            summary = "Consultar informacion de la aplicacion",
            description = "Devuelve nombre, descripcion, version, perfil activo y timestamp de la API."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Metadatos de la aplicacion obtenidos correctamente"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno inesperado",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping("/info")
    public ResponseEntity<ApplicationInfoResponse> info() {
        return ResponseEntity.ok(applicationInfoService.getInfo());
    }
}
