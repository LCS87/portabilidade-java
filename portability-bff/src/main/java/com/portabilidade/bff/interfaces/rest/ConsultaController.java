package com.portabilidade.bff.interfaces.rest;

import com.portabilidade.bff.application.ConsultaService;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Endpoint da tela de consulta (orientado a tela — convenção BFF). */
@RestController
@RequestMapping("/bff/v1")
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    public record ConsultaRequest(@NotEmpty List<String> numeros) { }

    @PostMapping("/consulta")
    public Mono<Map<String, Object>> consultar(@RequestBody ConsultaRequest request) {
        return consultaService.consultar(request.numeros());
    }
}
