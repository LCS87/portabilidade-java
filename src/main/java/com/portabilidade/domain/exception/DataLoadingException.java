package com.portabilidade.domain.exception;

/**
 * Lançada quando há falha no carregamento de dados externos
 * (CSV de operadoras, JSON de prefixos, banco DuckDB).
 */
public class DataLoadingException extends RuntimeException {

    public DataLoadingException(String message) {
        super(message);
    }

    public DataLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
