package com.portabilidade.bff.application;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Port hexagonal do BFF: contrato da API core. A implementação (WebClient)
 * vive em infrastructure/client — use cases e controllers conhecem só isto.
 */
public interface ApiClientPort {

    Mono<Map<String, Object>> consultar(List<String> numeros);

    Mono<Map<String, Object>> submitBatch(byte[] content, String fileName, String contentType);

    Mono<Map<String, Object>> batchStatus(String jobId);

    Mono<byte[]> batchResultCsv(String jobId);

    /** Dicionário de operadoras servido pela própria API core (dict carregado no boot). */
    Mono<List<Map<String, String>>> operadoras();
}
