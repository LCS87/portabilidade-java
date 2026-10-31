package com.portabilidade.infrastructure.web;

import java.time.Instant;

/** Status de um job de batch (GET /api/v1/portabilidade/batch/{id}). */
public record BatchStatusResponse(
        String id,
        String arquivo,
        String status,
        long linhasProcessadas,
        long totalLinhas,
        Instant criadoEm,
        String erro
) {
}
