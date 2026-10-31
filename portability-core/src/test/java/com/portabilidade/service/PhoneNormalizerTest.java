package com.portabilidade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhoneNormalizer")
class PhoneNormalizerTest {

    private PhoneNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new PhoneNormalizer();
    }

    @Test
    @DisplayName("Número já com 11 dígitos retorna Long correto")
    void normalizeElevenDigits() {
        Long result = normalizer.normalize("11987654321");
        assertEquals(11987654321L, result);
    }

    @Test
    @DisplayName("Número fixo com 10 dígitos é mantido como 10 (NÃO insere 9)")
    void normalizeTenDigitsKeepsTen() {
        // Linha fixa: 11 8765-4321 -> 1187654321 (10 dígitos, sem o 9)
        Long result = normalizer.normalize("1187654321");
        assertEquals(1187654321L, result);
    }

    @Test
    @DisplayName("Número com DDI 55 é removido")
    void normalizeDdiRemoved() {
        Long result = normalizer.normalize("5511987654321");
        assertEquals(11987654321L, result);
    }

    @Test
    @DisplayName("Número com DDI 55 e 12 dígitos (fixo) vira 10 dígitos")
    void normalizeDdiAndTenDigits() {
        Long result = normalizer.normalize("551187654321");
        assertEquals(1187654321L, result);
    }

    @Test
    @DisplayName("Número com formatação (parênteses, hífen) é normalizado")
    void normalizeFormatted() {
        Long result = normalizer.normalize("(11) 98765-4321");
        assertEquals(11987654321L, result);
    }

    @Test
    @DisplayName("Número com + e espaços é normalizado")
    void normalizePlusSign() {
        Long result = normalizer.normalize("+55 11 98765-4321");
        assertEquals(11987654321L, result);
    }

    @Test
    @DisplayName("Número inválido (curto demais) retorna null")
    void normalizeShortReturnsNull() {
        assertNull(normalizer.normalize("1234"));
    }

    @Test
    @DisplayName("String vazia retorna null")
    void normalizeEmptyReturnsNull() {
        assertNull(normalizer.normalize(""));
    }

    @Test
    @DisplayName("null retorna null")
    void normalizeNullReturnsNull() {
        assertNull(normalizer.normalize(null));
    }

    @Test
    @DisplayName("getCleanDigits retorna 11 dígitos para número válido")
    void getCleanDigitsValid() {
        String result = normalizer.getCleanDigits("(11) 98765-4321");
        assertEquals("11987654321", result);
    }

    @ParameterizedTest
    @DisplayName("Vários formatos válidos")
    @CsvSource({
        "11987654321,   11987654321",
        "1187654321,    1187654321",
        "5511987654321, 11987654321",
        "551187654321,  1187654321"
    })
    void normalizeVariousFormats(String input, String expected) {
        Long result = normalizer.normalize(input.trim());
        assertEquals(Long.parseLong(expected.trim()), result);
    }
}
