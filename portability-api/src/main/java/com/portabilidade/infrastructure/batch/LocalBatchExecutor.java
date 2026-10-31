package com.portabilidade.infrastructure.batch;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executor local de jobs batch usando virtual threads (Java 21).
 *
 * <p>Ativo quando {@code app.batch.queue.enabled=false} (padrão). Com a fila
 * ligada, {@link QueuedBatchExecutor} assume o port {@code BatchExecutor}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.batch.queue.enabled", havingValue = "false", matchIfMissing = true)
public class LocalBatchExecutor implements SubmitBatchUseCase.BatchExecutor {

    private static final Logger log = LoggerFactory.getLogger(LocalBatchExecutor.class);

    private final BatchExecutionService executionService;
    private final InMemoryBatchJobStore registry;
    private final ExecutorService executor;

    public LocalBatchExecutor(BatchExecutionService executionService,
                              InMemoryBatchJobStore registry) {
        this.executionService = executionService;
        this.registry = registry;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public String submit(Path savedFile, String originalFileName) {
        String id = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(id, originalFileName);
        registry.register(job);
        executor.submit(() -> executionService.execute(job, savedFile));
        return id;
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
            log.warn("Shutdown do executor local interrompido");
        }
    }
}
