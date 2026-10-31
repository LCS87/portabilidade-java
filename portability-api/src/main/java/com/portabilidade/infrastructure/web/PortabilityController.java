package com.portabilidade.infrastructure.web;

import com.portabilidade.application.usecase.ConsultPortabilityUseCase;
import com.portabilidade.service.ProcessResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de entrada REST (infrastructure/web). Conhece apenas o use case —
 * nunca o DuckDB/resolvers diretamente (hexagonal).
 */
@RestController
@RequestMapping("/api/v1/portabilidade")
@Tag(name = "Portabilidade", description = "Consulta de operadora/portadora por número")
public class PortabilityController {

    private final ConsultPortabilityUseCase consultUseCase;
    private final Map<String, String> operatorsDict;

    public PortabilityController(ConsultPortabilityUseCase consultUseCase,
                                 @Qualifier("operatorsDict") Map<String, String> operatorsDict) {
        this.consultUseCase = consultUseCase;
        this.operatorsDict = operatorsDict;
    }

    @Operation(summary = "Consulta 1..N números sincronamente (max 100)")
    @PostMapping("/consultar")
    public ResponseEntity<ConsultResponse> consultar(@Valid @RequestBody ConsultRequest request) {
        ProcessResult result = consultUseCase.consult(request.numeros());

        List<ConsultResponse.Item> itens = new ArrayList<>(result.size());
        for (int i = 0; i < result.size(); i++) {
            String raw = result.rawNumbers().get(i);
            itens.add(new ConsultResponse.Item(
                    raw,
                    consultUseCase.canonical(raw),
                    result.operatorAt(i),
                    result.sourceAt(i)));
        }
        return ResponseEntity.ok(new ConsultResponse(itens));
    }

    @Operation(summary = "Dicionario de operadoras (codigo -> nome) para selects/autocomplete")
    @GetMapping("/operadoras")
    public ResponseEntity<List<Map<String, String>>> operadoras() {
        List<Map<String, String>> out = new ArrayList<>(operatorsDict.size());
        operatorsDict.forEach((code, name) -> out.add(Map.of("codigo", code, "nome", name)));
        return ResponseEntity.ok(out);
    }
}
