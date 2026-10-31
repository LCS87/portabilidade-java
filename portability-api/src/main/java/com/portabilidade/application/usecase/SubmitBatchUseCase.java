package com.portabilidade.application.usecase;

import com.portabilidade.infrastructure.batch.BatchJob;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Use case assíncrono: submete um arquivo (CSV/XLSX) para processamento em
 * background e expõe consulta de status. Depende apenas dos ports internos
 * {@link BatchExecutor} e {@link JobRegistry} — a implementação
 * (InMemoryBatchJobStore + virtual threads) é infraestrutura plugável
 * (fila durável entra na Fase 4 do PLANO_MELHORIAS).
 */
@Service
public class SubmitBatchUseCase {

    /** Port: aceita um arquivo já persistido e devolve o id do job criado. */
    public interface BatchExecutor {
        String submit(Path savedFile, String originalFileName);
    }

    /** Port: recuperação do estado do job. */
    public interface JobRegistry {
        Optional<BatchJob> find(String id);
    }

    private final BatchExecutor executor;
    private final JobRegistry registry;

    public SubmitBatchUseCase(BatchExecutor executor, JobRegistry registry) {
        this.executor = executor;
        this.registry = registry;
    }

    public String submit(Path savedFile, String originalFileName) {
        return executor.submit(savedFile, originalFileName);
    }

    public Optional<BatchJob> status(String jobId) {
        return registry.find(jobId);
    }

    /** Erro de domínio: arquivo acima do limite configurado de linhas. */
    public static class TooManyRowsException extends RuntimeException {
        public TooManyRowsException(long maxRows) {
            super("limite de " + maxRows + " linhas excedido");
        }
    }
}
