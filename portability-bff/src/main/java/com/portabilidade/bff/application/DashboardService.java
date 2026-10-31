package com.portabilidade.bff.application;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use case do dashboard: agrega métricas a partir dos resultados de consulta
 * enviados pela UI (o BFF não lê o DuckDB diretamente — regra do plano).
 */
@Service
public class DashboardService {

    /** body: {"consultas":[{"numero":"...","operadora":"...","fonte":"..."}]} */
    public Mono<Map<String, Object>> resumo(List<Map<String, String>> consultas) {
        return Mono.fromSupplier(() -> {
            long total = consultas.size();
            long hits = consultas.stream().filter(c -> notBlank(c.get("operadora"))).count();
            Map<String, Long> porOperadora = new LinkedHashMap<>();
            Map<String, Long> porFonte = new LinkedHashMap<>();
            for (Map<String, String> c : consultas) {
                if (notBlank(c.get("operadora"))) {
                    porOperadora.merge(c.get("operadora"), 1L, Long::sum);
                }
                if (notBlank(c.get("fonte"))) {
                    porFonte.merge(c.get("fonte"), 1L, Long::sum);
                }
            }
            List<Map<String, Object>> topOperadoras = new ArrayList<>();
            porOperadora.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> topOperadoras.add(Map.of("operadora", e.getKey(), "quantidade", e.getValue())));
            List<Map<String, Object>> fontes = new ArrayList<>();
            porFonte.forEach((k, v) -> fontes.add(Map.of("fonte", k, "quantidade", v)));
            fontes.sort(Comparator.comparing(m -> -((Long) m.get("quantidade"))));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("totalConsultas", total);
            out.put("resolvidas", hits);
            out.put("taxaAcerto", total == 0 ? 0.0 : Math.round(hits * 10000.0 / total) / 100.0);
            out.put("topOperadoras", topOperadoras);
            out.put("porFonte", fontes);
            return out;
        });
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
