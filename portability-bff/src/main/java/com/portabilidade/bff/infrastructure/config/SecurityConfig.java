package com.portabilidade.bff.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Auth do usuário final (Fase 3): JWT resource server.
 * Perfil default/dev: endpoints liberados para desenvolvimento do front.
 * Produção: definir bff.security.mode=jwt + issuer-uri (spring.oauth2.resource-server.jwt).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeExchange(ex -> ex
                        .pathMatchers("/bff/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        .anyExchange().authenticated())
                .build();
    }
}
