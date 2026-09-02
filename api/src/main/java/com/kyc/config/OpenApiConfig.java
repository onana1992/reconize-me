package com.kyc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recognizMeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recogniz-Me API")
                        .description("Vérification d’identité — une identité numérique reconnue comme réelle")
                        .version("v1"))
                .servers(List.of(new Server().url("/").description("Local")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer-api-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("API Key")
                                        .description("Clé `ky_test_…` ou `ky_live_…`")));
    }
}
