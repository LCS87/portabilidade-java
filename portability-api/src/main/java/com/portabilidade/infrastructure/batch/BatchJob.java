package com.portabilidade.infrastructure.batch;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Estado de um job de batch submetido via API (Fase 2).
 * Persistência em memória agora; fila durável entra na Fase 4 do PLANO_MELHORIAS.
 */
public final class BatchJob {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    private final String id;
    private final String originalFileName;
    private final Instant createdAt;
    private final AtomicLong processedRows = new AtomicLong();

    private volatile Status status = Status.PENDING;
    private volatile long totalRows = -1;          // -1 = ainda desconhecido
    private volatile String resultPath;            // arquivo CSV FINAL_* gerado
    private volatile String errorMessage;

    public BatchJob(String id, String originalFileName) {
        this.id = id;
        this.originalFileName = originalFileName;
        this.createdAt = Instant.now();
    }

    public String getId()       { return id; }
    public String getOriginalFileName() { return originalFileName; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus()   { return status; }
    public long getTotalRows()  { return totalRows; }
    public long getProcessedRows() { return processedRows.get(); }
    public String getResultPath() { return resultPath; }
    public String getErrorMessage() { return errorMessage; }

    public void setStatus(Status s)             { this.status = s; }
    void setTotalRows(long t)            { this.totalRows = t; }
    public void setResultPath(String p)         { this.resultPath = p; }
    void setErrorMessage(String m)       { this.errorMessage = m; }

    /** Chamado pelos workers a cada chunk processado (medidor de progresso). */
    public void addProcessed(long n)     { processedRows.addAndGet(n); }

    /** Aborta o arquivo de saída temporário (chamado pelo writer no fluxo de falha). */
    void abortResultQuietly()            { /* path ainda não finalizado; limpeza feita pelo writer */ }
}
