package com.portabilidade.bff.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchServiceTest {

    @Test
    void computesProgressPercent() {
        Map<String, Object> ui = BatchService.toUi(Map.of(
                "status", "RUNNING", "linhasProcessadas", 25_000L, "totalLinhas", 50_000L));
        assertEquals(50L, ((Number) ui.get("progressoPct")).longValue());
    }

    @Test
    void zeroWhenTotalUnknown() {
        Map<String, Object> ui = BatchService.toUi(Map.of(
                "status", "PENDING", "linhasProcessadas", 0L, "totalLinhas", -1L));
        assertEquals(0L, ((Number) ui.get("progressoPct")).longValue());
    }
}
