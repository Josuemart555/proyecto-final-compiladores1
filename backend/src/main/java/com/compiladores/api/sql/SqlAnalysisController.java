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

@Tag(
        name = "SQL",
        description = "Analisis de sintaxis, tokens, AST, diagnosticos y reglas por dialecto."
)
@RestController
@RequestMapping("/api/v1/sql")
public class SqlAnalysisController {

    private final SqlAnalysisService sqlAnalysisService;

    public SqlAnalysisController(SqlAnalysisService sqlAnalysisService) {
        this.sqlAnalysisService = sqlAnalysisService;
    }

    @Operation(
            summary = "Analizar consulta por dialecto",
            description = """
                    Valida una consulta escrita en MySQL, SQL Server, PostgreSQL o MongoDB.

                    La respuesta HTTP 200 significa que la solicitud fue procesada por la API.
                    Para saber si la consulta es valida, revisar el campo `valid`.

                    - `valid=true`: no se encontraron errores bloqueantes.
                    - `valid=false`: la consulta tiene errores; revisar `diagnostics`.

                    El servicio no ejecuta consultas contra una base de datos real. Su objetivo es analizar sintaxis,
                    tokens, AST, simbolos detectados y reglas incompatibles por dialecto.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Consulta y dialecto que se desea validar.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SqlAnalysisRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "MySQL valido",
                                    summary = "SELECT con filtro y ordenamiento",
                                    value = """
                                            {
                                              "dialect": "mysql",
                                              "sql": "SELECT id, nombre, email FROM clientes WHERE estado = 'activo' ORDER BY nombre;"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "SQL Server valido",
                                    summary = "Uso de TOP",
                                    value = """
                                            {
                                              "dialect": "sqlserver",
                                              "sql": "SELECT TOP 10 ProductoID, Nombre, StockActual FROM Inventario WHERE StockActual < NivelReorden ORDER BY StockActual ASC;"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "PostgreSQL valido",
                                    summary = "Uso de ILIKE",
                                    value = """
                                            {
                                              "dialect": "postgresql",
                                              "sql": "SELECT id, nombre, email FROM usuarios WHERE email ILIKE '%gmail.com%';"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "MongoDB valido",
                                    summary = "find con filtro y proyeccion",
                                    value = """
                                            {
                                              "dialect": "mongodb",
                                              "sql": "db.usuarios.find({ pais: \\"GT\\", estado: \\"activo\\" }, { _id: 0, nombre: 1, edad: 1 });"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Error de dialecto",
                                    summary = "TOP no es valido en MySQL",
                                    value = """
                                            {
                                              "dialect": "mysql",
                                              "sql": "SELECT TOP 10 * FROM usuarios;"
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Analisis realizado. Revisar `valid` para saber si la consulta paso la validacion.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SqlAnalysisResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "consultaValida",
                                            summary = "Respuesta sin errores",
                                            value = """
                                                    {
                                                      "normalizedSql": "SELECT id, nombre FROM usuarios LIMIT 10;",
                                                      "characterCount": 41,
                                                      "lineCount": 1,
                                                      "statementCount": 1,
                                                      "trailingSemicolon": true,
                                                      "analyzedAt": "2026-05-23T16:20:00.000Z",
                                                      "valid": true,
                                                      "statementType": "SELECT",
                                                      "tokens": [
                                                        {"type": "KEYWORD", "lexeme": "SELECT", "line": 1, "column": 1},
                                                        {"type": "IDENTIFIER", "lexeme": "id", "line": 1, "column": 8}
                                                      ],
                                                      "ast": {
                                                        "type": "Statement",
                                                        "label": "SELECT",
                                                        "value": "",
                                                        "children": []
                                                      },
                                                      "diagnostics": [],
                                                      "semantic": {
                                                        "connected": true,
                                                        "tables": ["usuarios"],
                                                        "columns": ["id", "nombre"],
                                                        "warnings": []
                                                      },
                                                      "execution": {
                                                        "executed": false,
                                                        "message": "Sin ejecucion: este servicio opera en modo analisis sin conexion a base de datos.",
                                                        "elapsedMs": 0,
                                                        "rowCount": 0,
                                                        "columns": [],
                                                        "rows": []
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "consultaInvalida",
                                            summary = "Respuesta HTTP 200 con valid=false",
                                            value = """
                                                    {
                                                      "normalizedSql": "SELECT TOP 10 * FROM usuarios;",
                                                      "characterCount": 30,
                                                      "lineCount": 1,
                                                      "statementCount": 1,
                                                      "trailingSemicolon": true,
                                                      "analyzedAt": "2026-05-23T16:20:00.000Z",
                                                      "valid": false,
                                                      "statementType": "SELECT",
                                                      "tokens": [],
                                                      "ast": {
                                                        "type": "Statement",
                                                        "label": "SELECT",
                                                        "value": "",
                                                        "children": []
                                                      },
                                                      "diagnostics": [
                                                        {
                                                          "phase": "PARSER",
                                                          "severity": "ERROR",
                                                          "message": "La clausula TOP no es compatible con MySQL. Use LIMIT.",
                                                          "line": 1,
                                                          "column": 8
                                                        }
                                                      ],
                                                      "semantic": {
                                                        "connected": true,
                                                        "tables": ["usuarios"],
                                                        "columns": ["TOP 10 *"],
                                                        "warnings": []
                                                      },
                                                      "execution": {
                                                        "executed": false,
                                                        "message": "No se ejecuto la consulta debido a errores de sintaxis o semantica.",
                                                        "elapsedMs": 0,
                                                        "rowCount": 0,
                                                        "columns": [],
                                                        "rows": []
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
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
