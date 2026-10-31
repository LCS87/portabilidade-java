package com.portabilidade.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Exceções de domínio")
class ExceptionTest {

    @Test
    @DisplayName("DataLoadingException com mensagem e causa")
    void dataLoadingException() {
        var cause = new IllegalStateException("root");
        assertEquals("m", new DataLoadingException("m").getMessage());
        var e = new DataLoadingException("m", cause);
        assertEquals("m", e.getMessage());
        assertSame(cause, e.getCause());
        assertInstanceOf(RuntimeException.class, e);
    }

    @Test
    @DisplayName("PhoneNormalizationException com mensagem e causa")
    void phoneNormalizationException() {
        var cause = new IllegalArgumentException("root");
        assertEquals("m", new PhoneNormalizationException("m").getMessage());
        var e = new PhoneNormalizationException("m", cause);
        assertEquals("m", e.getMessage());
        assertSame(cause, e.getCause());
        assertInstanceOf(RuntimeException.class, e);
    }
}
