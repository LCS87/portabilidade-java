package com.portabilidade.service;

import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortabilityResolutionService")
class PortabilityResolutionServiceTest {

    @Mock private PortabilityRepository repository;
    @Mock private PrefixResolver prefixResolver;

    private PhoneNormalizer normalizer;
    private Map<String, String> operatorsDict;
    private PortabilityResolutionService service;

    @BeforeEach
    void setUp() {
        normalizer = new PhoneNormalizer();
        operatorsDict = Map.of("55321", "CLARO", "56789", "VIVO");
        service = new PortabilityResolutionService(repository, prefixResolver, normalizer, operatorsDict);
    }

    @Test
    @DisplayName("Número encontrado no DuckDB usa fonte 'portabilidade'")
    void foundInDuckDb() {
        when(repository.findOperatorsByPhoneNumbers(anyList()))
                .thenReturn(Map.of(11987654321L, "55321"));

        ProcessResult result = service.processNumbers(List.of("11987654321"));

        assertEquals("CLARO", result.operatorMap().get("11987654321"));
        assertEquals("portabilidade", result.sourceMap().get("11987654321"));
        verifyNoInteractions(prefixResolver);
    }

    @Test
    @DisplayName("Número não encontrado no DuckDB usa prefixo como fallback")
    void fallbackToPrefix() {
        when(repository.findOperatorsByPhoneNumbers(anyList()))
                .thenReturn(Map.of()); // nada no banco
        when(prefixResolver.resolve("11987654321")).thenReturn("TIM");

        ProcessResult result = service.processNumbers(List.of("11987654321"));

        assertEquals("TIM", result.operatorMap().get("11987654321"));
        assertEquals("prefixo", result.sourceMap().get("11987654321"));
    }

    @Test
    @DisplayName("Número não encontrado em nenhum lugar → nao_identificado")
    void notFound() {
        when(repository.findOperatorsByPhoneNumbers(anyList()))
                .thenReturn(Map.of());
        when(prefixResolver.resolve(anyString())).thenReturn(null);

        ProcessResult result = service.processNumbers(List.of("11987654321"));

        assertEquals("nao_identificado", result.operatorMap().get("11987654321"));
        assertEquals("nao_identificado", result.sourceMap().get("11987654321"));
    }

    @Test
    @DisplayName("Número inválido → fonte 'invalido'")
    void invalidPhone() {
        ProcessResult result = service.processNumbers(List.of("1234"));

        assertEquals("nao_identificado", result.operatorMap().get("1234"));
        assertEquals("invalido", result.sourceMap().get("1234"));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Número fixo (10 dígitos) NÃO recebe 9 e é consultado como 10 dígitos")
    void fixedLineTenDigitsNotMangled() {
        // Fixo 11 3333-4444 -> normalizado deve permanecer 10 dígitos (1133334444)
        when(repository.findOperatorsByPhoneNumbers(anyList()))
                .thenAnswer(inv -> {
                    List<Long> nums = inv.getArgument(0);
                    // O repositório deve receber o número COMO 10 dígitos, não 11
                    assertEquals(List.of(1133334444L), nums);
                    return Map.of(1133334444L, "55321");
                });

        ProcessResult result = service.processNumbers(List.of("1133334444"));

        assertEquals("CLARO", result.operatorMap().get("1133334444"));
        assertEquals("portabilidade", result.sourceMap().get("1133334444"));
    }

    @Test
    @DisplayName("Lista mista: válido portabilidade, válido prefixo, inválido")
    void mixedList() {
        when(repository.findOperatorsByPhoneNumbers(anyList()))
                .thenReturn(Map.of(11987654321L, "55321")); // só o primeiro encontrado no banco
        when(prefixResolver.resolve("21912345678")).thenReturn("VIVO"); // fallback para o segundo

        ProcessResult result = service.processNumbers(
                List.of("11987654321", "21912345678", "999")
        );

        // Portabilidade
        assertEquals("CLARO", result.operatorMap().get("11987654321"));
        assertEquals("portabilidade", result.sourceMap().get("11987654321"));

        // Prefixo
        assertEquals("VIVO", result.operatorMap().get("21912345678"));
        assertEquals("prefixo", result.sourceMap().get("21912345678"));

        // Inválido
        assertEquals("nao_identificado", result.operatorMap().get("999"));
        assertEquals("invalido", result.sourceMap().get("999"));
    }
}
