package com.portabilidade.bff.application;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

class DashboardServiceTest {

    @Test
    void aggregatesRateTopOperatorsAndSources() {
        DashboardService svc = new DashboardService();
        var consultas = List.of(
                Map.of("numero", "1", "operadora", "VIVO", "fonte", "DUCKDB"),
                Map.of("numero", "2", "operadora", "VIVO", "fonte", "JSON"),
                Map.of("numero", "3", "operadora", "", "fonte", "NONE"));

        StepVerifier.create(svc.resumo(consultas))
                .assertNext(out -> {
                    assert out.get("totalConsultas").equals(3L);
                    assert out.get("resolvidas").equals(2L);
                    assert out.get("taxaAcerto").equals(66.67);
                    List<?> top = (List<?>) out.get("topOperadoras");
                    assert !top.isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void emptyInputYieldsZeroRate() {
        StepVerifier.create(new DashboardService().resumo(List.of()))
                .assertNext(out -> {
                    assert out.get("totalConsultas").equals(0L);
                    assert out.get("taxaAcerto").equals(0.0);
                })
                .verifyComplete();
    }
}
