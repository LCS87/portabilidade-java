package com.portabilidade.bff.infrastructure.client;

import com.portabilidade.bff.application.ApiClientPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Adaptador de saída do BFF: chama a API core via HTTP (WebClient não-bloqueante).
 * Nunca acessa DuckDB/arquivos diretamente (regra da Fase 3 do plano).
 */
public class WebApiClient implements ApiClientPort {

    private final WebClient web;

    public WebApiClient(WebClient web) {
        this.web = web;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> consultar(List<String> numeros) {
        return web.post()
                .uri("/api/v1/portabilidade/consultar")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("numeros", numeros))
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> submitBatch(byte[] content, String fileName, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("arquivo", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }).contentType(MediaType.parseMediaType(contentType));
        return web.post()
                .uri("/api/v1/portabilidade/batch")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> batchStatus(String jobId) {
        return web.get()
                .uri("/api/v1/portabilidade/batch/{id}", jobId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @Override
    public Mono<byte[]> batchResultCsv(String jobId) {
        return web.get()
                .uri("/api/v1/portabilidade/batch/{id}/resultado", jobId)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, String>>> operadoras() {
        return web.get()
                .uri("/api/v1/portabilidade/operadoras")
                .retrieve()
                .bodyToMono(List.class)
                .map(l -> (List<Map<String, String>>) l);
    }
}
