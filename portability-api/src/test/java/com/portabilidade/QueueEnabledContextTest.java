package com.portabilidade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Regressão do conflito de beans: com a feature flag de fila ligada deve existir
 * apenas o QueuedBatchExecutor como BatchExecutor. O listener é desligado para
 * não exigir um RabbitMQ real no teste de contexto.
 */
@SpringBootTest(properties = {
        "app.batch.queue.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
class QueueEnabledContextTest {

    @Test
    void contextLoadsWithQueueEnabled() {
        // Falha com NoUniqueBeanDefinitionException se o executor local também estiver ativo.
    }
}
