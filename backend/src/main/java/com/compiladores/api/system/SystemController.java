package com.compiladores.api.system;

import com.compiladores.api.shared.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@Tag(name = "System", description = "Operaciones de disponibilidad, diagnostico rapido y metadatos de la aplicacion.")
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
            description = """
                    Devuelve una respuesta ligera para confirmar que la API esta disponible.
                    Usalo para pruebas rapidas desde Swagger, frontend, curl o herramientas como Postman.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Servicio disponible",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PingResponse.class),
                            examples = @ExampleObject(
                                    name = "pingOk",
                                    value = """
                                            {
                                              "status": "UP",
                                              "timestamp": "2026-05-23T16:20:00.000Z"
                                            }
                                            """
                            )
                    )
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
            description = "Devuelve nombre, descripcion, version, perfil activo de Spring y timestamp de la API."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Metadatos de la aplicacion obtenidos correctamente",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationInfoResponse.class),
                            examples = @ExampleObject(
                                    name = "applicationInfo",
                                    value = """
                                            {
                                              "name": "Compilador SQL API",
                                              "description": "API REST base para el proyecto final de compiladores",
                                              "version": "0.0.1-SNAPSHOT",
                                              "profile": "dev",
                                              "timestamp": "2026-05-23T16:20:00.000Z"
                                            }
                                            """
                            )
                    )
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
