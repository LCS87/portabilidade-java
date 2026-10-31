package com.portabilidade.infrastructure.messaging;

import com.portabilidade.infrastructure.batch.BatchExecutionService;
import com.portabilidade.infrastructure.batch.BatchJob;
import com.portabilidade.infrastructure.batch.InMemoryBatchJobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Consumer da fila portability.batch: registra o job no store e executa o
 * mesmo pipeline streaming da Fase 1/2 via BatchExecutionService.
 * Concorrência controlada por spring.rabbitmq.listener.simple.concurrency.
 */
@Component
@ConditionalOnProperty(name = "app.batch.queue.enabled", havingValue = "true")
public class BatchJobListener {

    private static final Logger log = LoggerFactory.getLogger(BatchJobListener.class);

    private final InMemoryBatchJobStore registry;
    private final BatchExecutionService executionService;

    public BatchJobListener(InMemoryBatchJobStore registry,
                            BatchExecutionService executionService) {
        this.registry = registry;
        this.executionService = executionService;
    }

    @RabbitListener(queues = BatchQueueConfig.BATCH_QUEUE)
    public void onMessage(BatchJobMessage message) {
        log.info("Job {} recebido da fila ({})", message.jobId(), message.originalFileName());
        BatchJob job = new BatchJob(message.jobId(), message.originalFileName());
        registry.register(job);
        executionService.execute(job, Path.of(message.filePath()));
    }
}
