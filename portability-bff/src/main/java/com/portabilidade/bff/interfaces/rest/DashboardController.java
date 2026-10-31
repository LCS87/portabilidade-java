package com.portabilidade.bff.interfaces.rest;

import com.portabilidade.bff.application.DashboardService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Endpoint da tela de dashboard: agrega métricas das consultas informadas pela UI. */
@RestController
@RequestMapping("/bff/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public record ResumoRequest(List<Map<String, String>> consultas) { }

    @PostMapping("/resumo")
    public Mono<Map<String, Object>> resumo(@RequestBody ResumoRequest request) {
        List<Map<String, String>> consultas =
                request.consultas() == null ? List.of() : request.consultas();
        return dashboardService.resumo(consultas);
    }
}
