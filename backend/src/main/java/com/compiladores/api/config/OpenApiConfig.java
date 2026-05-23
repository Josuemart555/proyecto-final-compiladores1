package com.compiladores.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String DEFAULT_VERSION = "0.0.1-SNAPSHOT";

    @Bean
    OpenAPI openApi(
            ApplicationProperties applicationProperties,
            ObjectProvider<BuildProperties> buildPropertiesProvider
    ) {
        return new OpenAPI()
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Servidor local de desarrollo")
                ))
                .tags(List.of(
                        new Tag()
                                .name("SQL")
                                .description("Analisis lexico, sintactico y semantico de consultas MySQL, SQL Server, PostgreSQL y MongoDB."),
                        new Tag()
                                .name("System")
                                .description("Endpoints de disponibilidad, salud basica y metadatos de la aplicacion.")
                ))
                .info(new Info()
                        .title(applicationProperties.name())
                        .description("""
                                API REST del proyecto final de Compiladores 1.

                                Permite validar consultas de MySQL, SQL Server, PostgreSQL y MongoDB sin conectarse a una base de datos real.
                                El endpoint principal devuelve tokens, AST, diagnosticos, simbolos detectados y un reporte de ejecucion simulada.

                                Importante: una respuesta HTTP 200 puede incluir `valid=false` si la consulta fue recibida correctamente,
                                pero contiene errores de sintaxis o reglas incompatibles con el dialecto seleccionado.
                                """)
                        .version(resolveVersion(buildPropertiesProvider.getIfAvailable()))
                        .contact(new Contact()
                                .name("Proyecto Final Compiladores 1")
                                .email("soporte@example.com"))
                        .license(new License()
                                .name("Uso academico")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentacion de uso del proyecto")
                        .url("/"));
    }

    private String resolveVersion(BuildProperties buildProperties) {
        if (buildProperties != null) {
            return buildProperties.getVersion();
        }

        String implementationVersion = OpenApiConfig.class.getPackage().getImplementationVersion();
        return implementationVersion != null ? implementationVersion : DEFAULT_VERSION;
    }
}
