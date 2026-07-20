package com.portabilidade.domain.model;

/**
 * Wrapper que carrega o número bruto (original) e sua versão normalizada (Long).
 * Se normalized == null, o número é inválido/não normalizável.
 */
public record PhoneNumber(String raw, Long normalized) {

    public boolean isValid() {
        return normalized != null;
    }
}
