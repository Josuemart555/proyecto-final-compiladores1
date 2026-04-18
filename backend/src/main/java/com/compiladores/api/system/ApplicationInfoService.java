package com.compiladores.api.system;

import com.compiladores.api.config.ApplicationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

@Service
public class ApplicationInfoService {

    private static final String DEFAULT_PROFILE = "default";
    private static final String DEFAULT_VERSION = "0.0.1-SNAPSHOT";

    private final ApplicationProperties applicationProperties;
    private final Environment environment;
    private final Clock clock;
    private final BuildProperties buildProperties;

    public ApplicationInfoService(
            ApplicationProperties applicationProperties,
            Environment environment,
            Clock clock,
            ObjectProvider<BuildProperties> buildPropertiesProvider
    ) {
        this.applicationProperties = applicationProperties;
        this.environment = environment;
        this.clock = clock;
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    public ApplicationInfoResponse getInfo() {
        return new ApplicationInfoResponse(
                applicationProperties.name(),
                applicationProperties.description(),
                resolveVersion(),
                resolveProfile(),
                Instant.now(clock)
        );
    }

    private String resolveVersion() {
        return buildProperties != null ? buildProperties.getVersion() : DEFAULT_VERSION;
    }

    private String resolveProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .findFirst()
                .orElse(DEFAULT_PROFILE);
    }
}
