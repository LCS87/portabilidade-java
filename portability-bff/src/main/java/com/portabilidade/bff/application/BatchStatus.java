package com.portabilidade.bff.application;

/**
 * Contrato de status de lote orientado à UI.
 * Normaliza os nomes da API core ({@code linhasProcessadas}/{@code totalLinhas})
 * para o vocabulário da tela ({@code processados}/{@code total}).
 */
public record BatchStatus(
        String jobId,
        String status,
        long processados,
        long total,
        int erros,
        int progressoPct,
        String erro
) {
}
