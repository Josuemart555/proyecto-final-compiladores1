package com.compiladores.api.sql;

import com.compiladores.api.shared.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SQL", description = "Operaciones para analizar sentencias SQL.")
@RestController
@RequestMapping("/api/v1/sql")
public class SqlAnalysisController {

    private final SqlAnalysisService sqlAnalysisService;

    public SqlAnalysisController(SqlAnalysisService sqlAnalysisService) {
        this.sqlAnalysisService = sqlAnalysisService;
    }

    @Operation(
            summary = "Analizar una consulta SQL",
            description = "Normaliza la consulta recibida y calcula metricas basicas sobre su contenido."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Consulta SQL analizada correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud invalida o JSON mal formado",
                    content = @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "validacion",
                                            value = """
                                                    {
                                                      "type": "about:blank",
                                                      "title": "Solicitud invalida",
                                                      "status": 400,
                                                      "detail": "Uno o mas campos no cumplen las reglas de validacion.",
                                                      "timestamp": "2026-04-18T16:45:12.120Z",
                                                      "errors": {
                                                        "sql": [
                                                          "El campo sql es obligatorio."
                                                        ]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "jsonInvalido",
                                            value = """
                                                    {
                                                      "type": "about:blank",
                                                      "title": "JSON invalido",
                                                      "status": 400,
                                                      "detail": "El cuerpo de la solicitud no contiene JSON valido.",
                                                      "timestamp": "2026-04-18T16:45:12.120Z"
                                                    }
                                                    """
                                    )
                            }
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
    @PostMapping("/analyze")
    public ResponseEntity<SqlAnalysisResponse> analyze(@Valid @RequestBody SqlAnalysisRequest request) {
        return ResponseEntity.ok(sqlAnalysisService.analyze(request));
    }
}
