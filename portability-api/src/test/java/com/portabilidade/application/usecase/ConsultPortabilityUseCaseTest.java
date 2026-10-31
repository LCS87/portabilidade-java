package com.portabilidade.application.usecase;

import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.ProcessResult;
import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fase 2: use case síncrono testado SEM Spring — apenas objetos do core,
 * comprovando que a camada de aplicação independe de infraestrutura.
 */
class ConsultPortabilityUseCaseTest {

    private ConsultPortabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        PortabilityRepository repo = numbers -> Map.of(11999999999L, "CLARO");
        // resolve() recebe os dígitos LIMPOS (11 dígitos), conforme contrato do port
        PrefixResolver prefixResolver = cleanDigits ->
                cleanDigits.startsWith("1188888888") ? "TIM" : null;
        PhoneNormalizer normalizer = new PhoneNormalizer();
        PortabilityResolutionService service =
                new PortabilityResolutionService(repo, prefixResolver, normalizer, Map.of());
        useCase = new ConsultPortabilityUseCase(service, normalizer);
    }

    @Test
    void resolvesPositionallyInInputOrder() {
        ProcessResult r = useCase.consult(List.of("(11) 99999-9999", "(11) 88888-8888"));

        assertEquals(2, r.size());
        assertEquals("CLARO", r.operatorAt(0));          // portabilidade
        assertEquals("TIM", r.operatorAt(1));            // fallback de prefixo
        assertEquals("portabilidade", r.sourceAt(0));
        assertEquals("prefixo", r.sourceAt(1));
    }

    @Test
    void rejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> useCase.consult(List.of()));
        assertThrows(IllegalArgumentException.class, () -> useCase.consult(null));
    }

    @Test
    void rejectsMoreThanSyncLimitAndPointsToBatch() {
        List<String> tooMany = java.util.stream.IntStream.range(0, ConsultPortabilityUseCase.MAX_SYNC_NUMBERS + 1)
                .mapToObj(i -> "119999900" + String.format("%02d", i))
                .toList();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.consult(tooMany));
        assertEquals(true, ex.getMessage().contains("batch"));
    }

    @Test
    void canonicalExposesNormalizedDigits() {
        assertEquals("11999999999", useCase.canonical("(11) 99999-9999"));
    }
}
