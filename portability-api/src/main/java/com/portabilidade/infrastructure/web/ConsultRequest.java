package com.portabilidade.infrastructure.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO de entrada do POST /api/v1/portabilidade/consultar (infrastructure/web —
 * a camada de aplicação nunca vê este tipo).
 */
public record ConsultRequest(
        @NotEmpty(message = "lista 'numeros' e obrigatoria")
        @Size(max = 100, message = "maximo de 100 numeros por consulta sincrona; use /batch")
        List<String> numeros
) {
}
