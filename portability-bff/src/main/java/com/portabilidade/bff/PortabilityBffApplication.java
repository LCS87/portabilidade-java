package com.portabilidade.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** BFF (Fase 3): backend dedicado ao frontend Angular, consome só a API core. */
@SpringBootApplication
public class PortabilityBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortabilityBffApplication.class, args);
    }
}
