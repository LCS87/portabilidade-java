package com.portabilidade.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portabilidade.application.usecase.ConsultPortabilityUseCase;
import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 2: contrato HTTP do endpoint síncrono via MockMvc standalone —
 * valida validação Bean Validation, mapeamento RFC 7807 e ordem posicional
 * da resposta sem subir contexto Spring nem DuckDB.
 */
class PortabilityControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;

    /** Fakes dos ports — o mesmo core usado em produção, sem infraestrutura. */
    private static ConsultPortabilityUseCase buildUseCase() {
        PortabilityRepository repo = numbers -> Map.of(11999999999L, "CLARO");
        PrefixResolver prefixResolver = clean -> null;
        PhoneNormalizer normalizer = new PhoneNormalizer();
        PortabilityResolutionService service =
                new PortabilityResolutionService(repo, prefixResolver, normalizer, Map.of());
        return new ConsultPortabilityUseCase(service, normalizer);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PortabilityController(buildUseCase()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void consultReturnsPositionalItemsInOrder() throws Exception {
        String body = mapper.writeValueAsString(Map.of("numeros", List.of("(11) 99999-9999", "999")));

        mockMvc.perform(post("/api/v1/portabilidade/consultar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].numero").value("(11) 99999-9999"))
                .andExpect(jsonPath("$.itens[0].telefoneNormalizado").value("11999999999"))
                .andExpect(jsonPath("$.itens[0].operadora").value("CLARO"))
                .andExpect(jsonPath("$.itens[0].fonte").value("portabilidade"))
                .andExpect(jsonPath("$.itens[1].fonte").value("invalido"));
    }

    @Test
    void emptyListIsRejectedWithRfc7807() throws Exception {
        String body = mapper.writeValueAsString(Map.of("numeros", List.of()));

        mockMvc.perform(post("/api/v1/portabilidade/consultar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void moreThanLimitIsRejectedWithGuidanceToBatch() throws Exception {
        List<String> tooMany = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "1199999" + String.format("%04d", i))
                .toList();
        String body = mapper.writeValueAsString(Map.of("numeros", tooMany));

        mockMvc.perform(post("/api/v1/portabilidade/consultar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("/batch")));
    }
}
