package com.portabilidade.infrastructure.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 4: habilita a metrica declarativa via @Timed nos use cases
 * (histograma p50/p95/p99 de portability_consult_seconds no Prometheus).
 */
@Configuration
public class MetricsConfig {

    @Bean
    TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
