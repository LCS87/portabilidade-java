package com.portabilidade.bff.interfaces.rest;

import com.portabilidade.bff.application.ApiClientPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Dicionário de operadoras para selects/autocomplete da UI (proxy da API core). */
@RestController
@RequestMapping("/bff/v1")
public class OperadorasController {

    private final ApiClientPort api;

    public OperadorasController(ApiClientPort api) {
        this.api = api;
    }

    @GetMapping("/operadoras")
    public Mono<List<Map<String, String>>> operadoras() {
        return api.operadoras();
    }
}
