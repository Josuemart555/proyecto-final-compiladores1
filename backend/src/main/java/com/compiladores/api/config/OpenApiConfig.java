package com.compiladores.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String DEFAULT_VERSION = "0.0.1-SNAPSHOT";

    @Bean
    OpenAPI openApi(
            ApplicationProperties applicationProperties,
            ObjectProvider<BuildProperties> buildPropertiesProvider
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationProperties.name())
                        .description(applicationProperties.description())
                        .version(resolveVersion(buildPropertiesProvider.getIfAvailable())));
    }

    private String resolveVersion(BuildProperties buildProperties) {
        if (buildProperties != null) {
            return buildProperties.getVersion();
        }

        String implementationVersion = OpenApiConfig.class.getPackage().getImplementationVersion();
        return implementationVersion != null ? implementationVersion : DEFAULT_VERSION;
    }
}
