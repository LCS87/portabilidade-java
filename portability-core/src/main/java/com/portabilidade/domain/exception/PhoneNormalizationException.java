package com.portabilidade.domain.exception;

/**
 * Lançada quando um número de telefone não pode ser normalizado por regra inválida.
 */
public class PhoneNormalizationException extends RuntimeException {

    public PhoneNormalizationException(String message) {
        super(message);
    }

    public PhoneNormalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
