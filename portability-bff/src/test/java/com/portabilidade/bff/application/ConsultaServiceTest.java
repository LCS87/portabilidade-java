package com.portabilidade.bff.application;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsultaServiceTest {

    /** Fake do port: devolve itens posicionalmente iguais aos recebidos. */
    private static ApiClientPort fakeApi(List<List<String>> received) {
        return new ApiClientPort() {
            @Override
            public Mono<Map<String, Object>> consultar(List<String> numeros) {
                received.add(new ArrayList<>(numeros));
                List<Map<String, Object>> itens = new ArrayList<>();
                for (String n : numeros) {
                    itens.add(Map.of(
                            "numero", n,
                            "canonical", n,
                            "operadora", n.startsWith("11") ? "VIVO" : "",
                            "fonte", "DUCKDB"));
                }
                return Mono.just(Map.of("itens", itens));
            }
            @Override
            public Mono<Map<String, Object>> submitBatch(byte[] c, String f, String t) { return Mono.empty(); }
            @Override
            public Mono<Map<String, Object>> batchStatus(String id) { return Mono.empty(); }
            @Override
            public Mono<byte[]> batchResultCsv(String id) { return Mono.empty(); }
            @Override
            public Mono<List<Map<String, String>>> operadoras() { return Mono.just(List.of()); }
        };
    }

    @Test
    void dedupesAndChunksBy100() {
        List<List<String>> received = new ArrayList<>();
        ConsultaService svc = new ConsultaService(fakeApi(received));
        List<String> input = new ArrayList<>();
        for (int i = 0; i < 250; i++) input.add("1190000000" + i % 200); // 200 únicos + duplicados

        StepVerifier.create(svc.consultar(input))
                .assertNext(out -> {
                    assertEquals(200, out.get("total"));
                    assertEquals(200L, ((Number) out.get("encontradas")).longValue());
                })
                .verifyComplete();
        assertEquals(2, received.size()); // 200 únicos → chunks 100+100
        assertEquals(100, received.get(0).size());
        assertEquals(100, received.get(1).size());
    }

    @Test
    void rejectsMoreThanMax() {
        ConsultaService svc = new ConsultaService(fakeApi(new ArrayList<>()));
        List<String> input = new ArrayList<>();
        for (int i = 0; i < ConsultaService.MAX_NUMEROS + 1; i++) input.add("11" + i);
        StepVerifier.create(svc.consultar(input))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void masksOperatorForUi() {
        assertEquals("VIV…", ConsultaService.mask("VIVO S.A."));
        assertEquals("TIM", ConsultaService.mask("TIM"));
        assertEquals(null, ConsultaService.mask("  "));
    }
}
