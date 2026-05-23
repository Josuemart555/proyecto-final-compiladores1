package com.compiladores.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${application.name}")
    private String applicationName;

    @Test
    void systemInfoReturnsApplicationMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Compilador SQL API"))
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    void sqlAnalyzeReturnsNormalizedData() throws Exception {
        mockMvc.perform(post("/api/v1/sql/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sql": "SELECT   *  FROM usuarios;  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedSql").value("SELECT * FROM usuarios;"))
                .andExpect(jsonPath("$.statementCount").value(1))
                .andExpect(jsonPath("$.trailingSemicolon").value(true));
    }

    @Test
    void sqlAnalyzeRejectsBlankSql() throws Exception {
        mockMvc.perform(post("/api/v1/sql/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sql": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"))
                .andExpect(jsonPath("$.errors.sql[0]").exists());
    }

    @Test
    void openApiDocsExposeConfiguredMetadataAndEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value(applicationName))
                .andExpect(jsonPath("$.paths['/api/v1/system/ping']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/system/info']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sql/analyze']").exists());
    }

    @Test
    void swaggerUiEndpointIsAvailable() throws Exception {
        MvcResult result = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).isBetween(200, 399);

        if (status >= 300) {
            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/swagger-ui/index.html");
        }
    }
}
