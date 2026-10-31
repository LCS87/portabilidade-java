package com.portabilidade.bff.interfaces.rest;

import com.portabilidade.bff.application.ApiClientPort;
import com.portabilidade.bff.application.BatchService;
import com.portabilidade.bff.application.ConsultaService;
import com.portabilidade.bff.application.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

class BffControllersTest {

    private WebTestClient client;

    private static final ApiClientPort FAKE_API = new ApiClientPort() {
        @Override
        public Mono<Map<String, Object>> consultar(List<String> numeros) {
            return Mono.just(Map.of("itens", numeros.stream()
                    .<Map<String, Object>>map(n -> Map.of(
                            "numero", n, "canonical", n,
                            "operadora", "VIVO", "fonte", "DUCKDB"))
                    .toList()));
        }
        @Override
        public Mono<Map<String, Object>> submitBatch(byte[] c, String f, String t) {
            return Mono.just(Map.of("id", "job-1", "status", "PENDING"));
        }
        @Override
        public Mono<Map<String, Object>> batchStatus(String id) {
            return Mono.just(Map.of("id", id, "status", "COMPLETED",
                    "linhasProcessadas", 10L, "totalLinhas", 10L));
        }
        @Override
        public Mono<byte[]> batchResultCsv(String id) {
            return Mono.just("telefone,operadora\n".getBytes());
        }
        @Override
        public Mono<List<Map<String, String>>> operadoras() {
            return Mono.just(List.of(Map.of("codigo", "123", "nome", "VIVO")));
        }
    };

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(
                        new ConsultaController(new ConsultaService(FAKE_API)),
                        new BatchController(new BatchService(FAKE_API)),
                        new DashboardController(new DashboardService()),
                        new OperadorasController(FAKE_API))
                .build();
    }

    @Test
    void consultaReturnsFormattedItems() {
        client.post().uri("/bff/v1/consulta")
                .bodyValue(Map.of("numeros", List.of("11999990000", "11999990000")))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)   // dedup no BFF
                .jsonPath("$.encontradas").isEqualTo(1)
                .jsonPath("$.itens[0].mascarado").isEqualTo("VIV…");
    }

    @Test
    void batchStatusSinglePollWorks() {
        client.get().uri("/bff/v1/batch/job-1")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.progressoPct").isEqualTo(100);
    }

    @Test
    void dashboardResumoAggregates() {
        client.post().uri("/bff/v1/dashboard/resumo")
                .bodyValue(Map.of("consultas", List.of(
                        Map.of("operadora", "VIVO", "fonte", "DUCKDB"))))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalConsultas").isEqualTo(1)
                .jsonPath("$.taxaAcerto").isEqualTo(100.0);
    }

    @Test
    void operadorasProxied() {
        client.get().uri("/bff/v1/operadoras")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].nome").isEqualTo("VIVO");
    }

    @Test
    void resultadoDownloadCsv() {
        client.get().uri("/bff/v1/batch/job-1/resultado")
                .exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/csv");
    }
}
