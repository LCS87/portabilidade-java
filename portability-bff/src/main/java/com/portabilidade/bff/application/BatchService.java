package com.portabilidade.bff.application;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use case da tela de upload: submete à API core e expõe stream de progresso
 * (polling → SSE-friendly) até COMPLETED/FAILED.
 */
@Service
public class BatchService {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final ApiClientPort api;

    public BatchService(ApiClientPort api) {
        this.api = api;
    }

    public Mono<Map<String, Object>> submit(byte[] content, String fileName) {
        return api.submitBatch(content, fileName, guessContentType(fileName));
    }

    /** Fluxo de status progressivos (para polling ou SSE no controller). */
    public Flux<Map<String, Object>> monitor(String jobId) {
        return Mono.defer(() -> api.batchStatus(jobId))
                .delayElement(POLL_INTERVAL)
                .repeat()
                .takeUntil(s -> isTerminal(s.get("status")))
                .timeout(TIMEOUT)
                .map(BatchService::toUi);
    }

    public Mono<byte[]> download(String jobId) {
        return api.batchResultCsv(jobId);
    }

    static Map<String, Object> toUi(Map<String, Object> raw) {
        Map<String, Object> ui = new LinkedHashMap<>(raw);
        Object total = raw.get("totalLinhas");
        Object done = raw.get("linhasProcessadas");
        if (total instanceof Number t && t.longValue() > 0 && done instanceof Number d) {
            ui.put("progressoPct", Math.min(100, d.longValue() * 100 / t.longValue()));
        } else {
            ui.put("progressoPct", 0);
        }
        return ui;
    }

    private static boolean isTerminal(Object status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private static String guessContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
    }

    static List<String> terminalStates() {
        return List.of("COMPLETED", "FAILED");
    }
}
