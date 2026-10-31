package com.portabilidade.infrastructure.batch;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro em memória dos jobs de batch.
 *
 * <p>Este adaptador implementa apenas o port {@link SubmitBatchUseCase.JobRegistry}.
 * A execução fica em {@link LocalBatchExecutor} (feature flag desligada) ou em
 * {@link QueuedBatchExecutor} (flag ligada), evitando dois beans do mesmo port.</p>
 */
@Component
public class InMemoryBatchJobStore implements SubmitBatchUseCase.JobRegistry {

    private final Map<String, BatchJob> jobs = new ConcurrentHashMap<>();

    @Override
    public Optional<BatchJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /** Usado pelos executores: registra um job já conhecido antes de executá-lo. */
    public void register(BatchJob job) {
        jobs.put(job.getId(), job);
    }
}
