package com.portabilidade.bff.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchServiceTest {

    @Test
    void mapsCoreStatusToUiContract() {
        BatchStatus ui = BatchService.toStatus(Map.of(
                "id", "job-1",
                "status", "RUNNING",
                "linhasProcessadas", 25_000L,
                "totalLinhas", 50_000L));
        assertEquals("job-1", ui.jobId());
        assertEquals(25_000L, ui.processados());
        assertEquals(50_000L, ui.total());
        assertEquals(0, ui.erros());
        assertEquals(50, ui.progressoPct());
    }

    @Test
    void zeroWhenTotalUnknown() {
        BatchStatus ui = BatchService.toStatus(Map.of(
                "id", "job-1",
                "status", "PENDING",
                "linhasProcessadas", 0L,
                "totalLinhas", -1L));
        assertEquals(0L, ui.progressoPct());
        assertEquals(0L, ui.total());
    }

    @Test
    void completedAlwaysShowsHundredPercent() {
        BatchStatus ui = BatchService.toStatus(Map.of(
                "id", "job-1",
                "status", "COMPLETED",
                "linhasProcessadas", 10L,
                "totalLinhas", 10L));
        assertEquals(100, ui.progressoPct());
    }

    @Test
    void failedJobExposesErrorCount() {
        BatchStatus ui = BatchService.toStatus(Map.of(
                "id", "job-1",
                "status", "FAILED",
                "linhasProcessadas", 3L,
                "totalLinhas", 10L,
                "erro", "coluna 'telefone' obrigatoria"));
        assertEquals(1, ui.erros());
        assertEquals("coluna 'telefone' obrigatoria", ui.erro());
        assertEquals(30, ui.progressoPct());
    }

    @Test
    void submissionNormalizesIdToJobId() {
        BatchSubmission ui = BatchService.toSubmission(Map.of(
                "id", "job-1", "status", "PENDING", "arquivo", "entrada.csv"));
        assertEquals("job-1", ui.jobId());
        assertEquals("PENDING", ui.status());
        assertEquals("entrada.csv", ui.arquivo());
    }
}
