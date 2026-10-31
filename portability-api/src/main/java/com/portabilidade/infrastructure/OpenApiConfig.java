package com.portabilidade.infrastructure;

import com.portabilidade.application.usecase.ConsultPortabilityUseCase;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 (springdoc) — documentação viva em /swagger-ui.html (Fase 2).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi portabilityApi() {
        return GroupedOpenApi.builder()
                .group("portabilidade-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public OpenAPI portabilityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Portabilidade Numerica API")
                        .version("1.0.0")
                        .description("Consulta de operadora/portadora por numero (sincrona ate "
                                + ConsultPortabilityUseCase.MAX_SYNC_NUMBERS
                                + " numeros) e processamento assincrono de arquivos CSV/XLSX. "
                                + "Erros em RFC 7807 (application/problem+json)."))
                .servers(java.util.List.of(
                        new Server().url("/").description("instancia atual")));
    }
}
