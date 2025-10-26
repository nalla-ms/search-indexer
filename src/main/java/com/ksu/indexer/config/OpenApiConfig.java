// src/main/java/com/example/indexer/config/OpenApiConfig.java
package com.ksu.indexer.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI api() {
        return new OpenAPI()
            .info(new Info()
                .title("Search Indexer API")
                .version("v1")
                .description("Endpoints for ingesting files and merging segments."));
    }
}