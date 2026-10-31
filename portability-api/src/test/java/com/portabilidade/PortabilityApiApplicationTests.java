package com.portabilidade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test do contexto padrão (fila desligada): garante que a API sobe com
 * os beans reais de produção, incluindo o executor local de batch.
 */
@SpringBootTest(properties = {
        "app.batch.queue.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class PortabilityApiApplicationTests {

    @Test
    void contextLoads() {
        // Falha se o wiring de CoreConfig/BatchExecutor/JobRegistry estiver quebrado.
    }
}
