package com.compiladores.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class CompiladorApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompiladorApiApplication.class, args);
    }

}
