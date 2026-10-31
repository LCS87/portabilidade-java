package com.portabilidade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fase 2 (PLANO_MELHORIAS): API REST hexagonal — Spring Boot 3.3 / Java 21.
 *
 * Camadas (dependências apontam para dentro):
 *   infrastructure/web  → application/usecases → ports (core)
 * A composição de dependências é feita em {@code CoreConfig} (@Configuration),
 * eliminando a DI manual do main do batch.
 */
@SpringBootApplication
public class PortabilityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortabilityApiApplication.class, args);
    }
}
