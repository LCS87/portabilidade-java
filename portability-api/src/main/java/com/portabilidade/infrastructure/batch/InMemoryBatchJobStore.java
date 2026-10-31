package com.portabilidade.infrastructure.batch;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementação dos ports BatchExecutor/JobRegistry (Fase 2, refatorada na Fase 4).
 *
 * Mantém o registro de jobs em memória e delega a execução do pipeline
 * streaming (chunks → resolução → escrita incremental) ao {@link BatchExecutionService},
 * que também é usado pelo listener da fila RabbitMQ quando
 * app.batch.queue.enabled=true. Execução local usa virtual threads (Java 21).
 */
@Component
public class InMemoryBatchJobStore
        implements SubmitBatchUseCase.BatchExecutor, SubmitBatchUseCase.JobRegistry {

    private final Map<String, BatchJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final BatchExecutionService executionService;

    public InMemoryBatchJobStore(BatchExecutionService executionService) {
        this.executionService = executionService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public String submit(Path savedFile, String originalFileName) {
        String id = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(id, originalFileName);
        jobs.put(id, job);
        executor.submit(() -> executionService.execute(job, savedFile));
        return id;
    }

    @Override
    public Optional<BatchJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /** Usado pelo listener da fila: registra um job já conhecido antes de executá-lo. */
    public void register(BatchJob job) {
        jobs.put(job.getId(), job);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
