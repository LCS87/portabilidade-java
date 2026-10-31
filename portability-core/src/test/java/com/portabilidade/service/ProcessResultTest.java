package com.portabilidade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProcessResult")
class ProcessResultTest {

    private static final ProcessResult SAMPLE = new ProcessResult(
            List.of("(11) 98765-4321", "11987654321", "21912345678"),
            List.of("CLARO", "CLARO", "TIM"),
            List.of("portabilidade", "prefixo", "prefixo"));

    @Test
    @DisplayName("acesso por posicao: operatorAt/sourceAt/size")
    void accessorsByPosition() {
        assertEquals(3, SAMPLE.size());
        assertEquals("CLARO", SAMPLE.operatorAt(0));
        assertEquals("portabilidade", SAMPLE.sourceAt(0));
        assertEquals("prefixo", SAMPLE.sourceAt(1));
        assertEquals("TIM", SAMPLE.operatorAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> SAMPLE.operatorAt(3));
    }

    @Test
    @DisplayName("busca por valor bruto retorna a PRIMEIRA ocorrencia (sem colisao)")
    void lookupByRawReturnsFirstOccurrence() {
        assertEquals("CLARO", SAMPLE.operatorFor("(11) 98765-4321"));
        assertEquals("portabilidade", SAMPLE.sourceFor("(11) 98765-4321"));
        assertNull(SAMPLE.operatorFor("nada"));
        assertNull(SAMPLE.sourceFor("nada"));
    }

    @Test
    @DisplayName("projecoes em mapa preservam primeira ocorrencia")
    void mapProjections() {
        var opMap = SAMPLE.toOperatorMap();
        var srcMap = SAMPLE.toSourceMap();
        assertEquals(3, opMap.size());
        assertEquals("CLARO", opMap.get("(11) 98765-4321"));
        assertEquals("portabilidade", srcMap.get("(11) 98765-4321"));
        opMap.put("x", "y"); // mapa de projeção é mutável por design (LinkedHashMap para logs/métricas)
        assertEquals("y", opMap.get("x"));
    }

    @Test
    @DisplayName("tamanhos divergentes entre raws/operators/sources falham rapido")
    void rejectsInconsistentSizes() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProcessResult(List.of("a", "b"), List.of("CLARO"), List.of("portabilidade")));
        assertThrows(IllegalArgumentException.class, () ->
                new ProcessResult(List.of("a"), List.of("CLARO", "TIM"), List.of("portabilidade")));
        assertThrows(IllegalArgumentException.class, () ->
                new ProcessResult(List.of("a"), List.of("CLARO"), List.of("portabilidade", "prefixo")));
    }

    @Test
    @DisplayName("nulls sao normalizados para listas vazias")
    void nullSafe() {
        ProcessResult r = new ProcessResult(null, null, null);
        assertEquals(0, r.size());
        assertNull(r.operatorFor("nada"));
    }
}
