package com.portabilidade.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Port de publicação: envia o job para processamento assíncrono durável. */
@Component
@ConditionalOnProperty(name = "app.batch.queue.enabled", havingValue = "true")
public class BatchJobProducer {

    private final RabbitTemplate template;

    public BatchJobProducer(RabbitTemplate batchRabbitTemplate) {
        this.template = batchRabbitTemplate;
    }

    public void publish(BatchJobMessage message) {
        template.convertAndSend(BatchQueueConfig.BATCH_QUEUE, message);
    }
}
