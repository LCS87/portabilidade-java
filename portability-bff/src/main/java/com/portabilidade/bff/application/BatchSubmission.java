package com.portabilidade.bff.application;

/**
 * Contrato de submissão de lote exposto à UI.
 * A API core devolve {@code id}; o BFF normaliza para {@code jobId}.
 */
public record BatchSubmission(
        String jobId,
        String status,
        String arquivo
) {
}
