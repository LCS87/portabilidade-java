package com.portabilidade.infrastructure.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 4: infraestrutura de fila durável (RabbitMQ) para jobs batch.
 * Ativada somente com a feature flag app.batch.queue.enabled=true —
 * sem fila, o executor em memória (virtual threads) continua servindo.
 */
@Configuration
@ConditionalOnProperty(name = "app.batch.queue.enabled", havingValue = "true")
public class BatchQueueConfig {

    public static final String BATCH_QUEUE = "portability.batch";

    @Bean
    Queue batchQueue() {
        // durable=true: mensagens sobrevivem a restart do broker
        return new Queue(BATCH_QUEUE, true);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate batchRabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory factory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        return template;
    }
}
