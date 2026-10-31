package com.portabilidade.bff.application;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Use case da tela de upload: submete à API core e expõe status/progresso
 * (polling → SSE-friendly) até COMPLETED/FAILED.
 *
 * <p>O contrato público deste serviço já é o da UI. A tradução dos nomes
 * internos da API core fica concentrada aqui, evitando drift entre telas.</p>
 */
@Service
public class BatchService {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final ApiClientPort api;

    public BatchService(ApiClientPort api) {
        this.api = api;
    }

    public Mono<BatchSubmission> submit(byte[] content, String fileName) {
        return api.submitBatch(content, fileName, guessContentType(fileName))
                .map(BatchService::toSubmission);
    }

    /** Uma consulta imediata de status (sem o atraso de 2 s do stream). */
    public Mono<BatchStatus> status(String jobId) {
        return api.batchStatus(jobId).map(BatchService::toStatus);
    }

    /**
     * Fluxo de status para SSE: primeira emissão imediata e depois polling a
     * cada 2 s até COMPLETED/FAILED.
     */
    public Flux<BatchStatus> monitor(String jobId) {
        return Flux.defer(() -> {
            Mono<Map<String, Object>> first = api.batchStatus(jobId);
            Flux<Map<String, Object>> next = Flux.interval(POLL_INTERVAL)
                    .concatMap(tick -> api.batchStatus(jobId));
            return Flux.concat(first, next)
                    .takeUntil(raw -> isTerminal(raw.get("status")))
                    .timeout(TIMEOUT);
        }).map(BatchService::toStatus);
    }

    public Mono<byte[]> download(String jobId) {
        return api.batchResultCsv(jobId);
    }

    static BatchSubmission toSubmission(Map<String, Object> raw) {
        String jobId = stringValue(raw.get("id"));
        if (jobId == null) {
            jobId = stringValue(raw.get("jobId"));
        }
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalStateException("API core nao retornou o id do job de batch");
        }
        String status = stringValue(raw.get("status"));
        String arquivo = stringValue(raw.get("arquivo"));
        return new BatchSubmission(jobId, status == null ? "PENDING" : status, arquivo);
    }

    static BatchStatus toStatus(Map<String, Object> raw) {
        String status = stringValue(raw.get("status"));
        if (status == null) {
            status = "PENDING";
        }

        long processados = numberValue(raw.get("linhasProcessadas"));
        if (processados < 0) {
            processados = numberValue(raw.get("processados"));
        }
        processados = Math.max(processados, 0);
        long total = numberValue(raw.get("totalLinhas"));
        if (total < 0) {
            total = numberValue(raw.get("total"));
        }
        total = Math.max(total, 0);

        String erro = stringValue(raw.get("erro"));
        int erros = raw.get("erros") instanceof Number n
                ? Math.max(n.intValue(), 0)
                : (erro == null || erro.isBlank() ? 0 : 1);

        int progressoPct;
        if ("COMPLETED".equalsIgnoreCase(status)) {
            progressoPct = 100;
        } else if (total > 0) {
            progressoPct = (int) Math.min(100, processados * 100 / total);
        } else {
            progressoPct = 0;
        }

        return new BatchStatus(
                stringValue(raw.get("id")) != null ? stringValue(raw.get("id")) : stringValue(raw.get("jobId")),
                status,
                processados,
                total,
                erros,
                progressoPct,
                erro);
    }

    private static boolean isTerminal(Object status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long numberValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String guessContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
    }
}
