package com.portabilidade.bff.application;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Use case da tela de consulta: deduplica, limita a 100 por chamada da API,
 * faz chunking transparente e formata para a UI (mascara LGPD + badge de fonte).
 */
@Service
public class ConsultaService {

    static final int MAX_NUMEROS = 500;
    private static final int API_CHUNK = 100; // limite sync da API core (Fase 2)

    private final ApiClientPort api;

    public ConsultaService(ApiClientPort api) {
        this.api = api;
    }

    public Mono<Map<String, Object>> consultar(List<String> numeros) {
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(numeros));
        if (clean.size() > MAX_NUMEROS) {
            return Mono.error(new IllegalArgumentException(
                    "limite de " + MAX_NUMEROS + " numeros por consulta no BFF"));
        }
        List<Mono<Map<String, Object>>> chunks = new ArrayList<>();
        for (int i = 0; i < clean.size(); i += API_CHUNK) {
            chunks.add(api.consultar(clean.subList(i, Math.min(i + API_CHUNK, clean.size()))));
        }
        return Mono.zip(chunks, arrays -> {
            List<Map<String, Object>> itens = new ArrayList<>();
            long encontradas = 0;
            for (Object a : arrays) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = (Map<String, Object>) a;
                Object raw = resp.get("itens");
                if (!(raw instanceof List<?> list)) continue;
                for (Object o : list) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) o;
                    Map<String, Object> ui = new LinkedHashMap<>();
                    ui.put("numero", item.get("numero"));
                    ui.put("canonical", item.get("canonical"));
                    String op = (String) item.get("operadora");
                    ui.put("operadora", op);
                    ui.put("fonte", item.get("fonte"));
                    ui.put("mascarado", mask(op));
                    boolean hit = op != null && !op.isBlank();
                    if (hit) encontradas++;
                    ui.put("encontrada", hit);
                    itens.add(ui);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", itens.size());
            out.put("encontradas", encontradas);
            out.put("itens", itens);
            return out;
        });
    }

    /** LGPD: preserva só os 3 primeiros caracteres do nome da operadora. */
    static String mask(String operator) {
        if (operator == null || operator.isBlank()) return null;
        String t = operator.trim();
        return t.length() <= 3 ? t : t.substring(0, 3) + "…";
    }
}
