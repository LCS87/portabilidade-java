package com.portabilidade.infrastructure.batch;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import com.portabilidade.infrastructure.messaging.BatchJobMessage;
import com.portabilidade.infrastructure.messaging.BatchJobProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Fase 4: BatchExecutor alternativo que publica o job na fila RabbitMQ
 * (processamento assíncrono durável, workers escaláveis) em vez de rodar
 * localmente. Ativado por feature flag app.batch.queue.enabled=true;
 * desligada a flag, o InMemoryBatchJobStore assume novamente — mesmo port,
 * transportes intercambiáveis (demonstração do valor da arquitetura hexagonal).
 */
@Component
@ConditionalOnProperty(name = "app.batch.queue.enabled", havingValue = "true")
public class QueuedBatchExecutor implements SubmitBatchUseCase.BatchExecutor {

    private final BatchJobProducer producer;
    private final InMemoryBatchJobStore registry;

    public QueuedBatchExecutor(BatchJobProducer producer, InMemoryBatchJobStore registry) {
        this.producer = producer;
        this.registry = registry;
    }

    @Override
    public String submit(Path savedFile, String originalFileName) {
        String id = java.util.UUID.randomUUID().toString();
        // registra como PENDING para o GET status funcionar imediatamente
        registry.register(new BatchJob(id, originalFileName));
        producer.publish(new BatchJobMessage(id, savedFile.toString(), originalFileName));
        return id;
    }
}
