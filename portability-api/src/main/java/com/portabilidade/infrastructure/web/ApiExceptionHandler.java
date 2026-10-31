package com.portabilidade.infrastructure.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Erros padronizados em RFC 7807 (ProblemDetails) — Fase 2 do PLANO_MELHORIAS.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** ResponseStatusException lançado pelos controllers → ProblemDetail com o status original. */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setTitle("Requisicao invalida");
        pd.setType(java.net.URI.create("https://portabilidade.errors/bad-request"));
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        // Spring 6.1: assinatura usa WebRequest — montamos o ProblemDetail RFC 7807 manualmente
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Requisicao invalida");
        pd.setType(java.net.URI.create("https://portabilidade.errors/validation"));
        return new ResponseEntity<>(pd, headers, status);
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "arquivo enviado excede o limite configurado");
        pd.setTitle("Payload muito grande");
        pd.setType(java.net.URI.create("https://portabilidade.errors/payload-too-large"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Requisicao invalida");
        pd.setType(java.net.URI.create("https://portabilidade.errors/bad-request"));
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "recurso de consulta indisponivel: " + ex.getMessage());
        pd.setTitle("Servico indisponivel");
        pd.setType(java.net.URI.create("https://portabilidade.errors/unavailable"));
        return pd;
    }
}
