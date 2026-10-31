package com.portabilidade.bff.infrastructure.config;

import com.portabilidade.bff.application.ApiClientPort;
import com.portabilidade.bff.infrastructure.client.WebApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/** Composition root do BFF: WebClient apontando para a API core. */
@Configuration
public class BffConfig {

    @Value("${bff.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    @Value("${bff.api.timeout-seconds:60}")
    private long timeoutSeconds;

    @Bean
    public WebClient apiWebClient() {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024)) // CSVs de resultado
                .build();
    }

    @Bean
    public ApiClientPort apiClientPort(WebClient apiWebClient) {
        return new WebApiClient(apiWebClient);
    }
}
