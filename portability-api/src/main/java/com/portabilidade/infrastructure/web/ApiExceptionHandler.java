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

    /**
     * ResponseStatusException é ErrorResponseException. Em Spring 6.1, um
     * @ExceptionHandler proprio para ela causaria ambiguidade de mapeamento com
     * o handler global herdado (erro na inicializacao do advice) — e remover o
     * advice inteiro faria o standalone MockMvc cair no DefaultHandlerExceptionResolver,
     * que nao resolve ResponseStatusException (vira 400). Entao mantemos a
     * classe registrada apenas por handlers proprios validos abaixo; o tratamento
     * RFC 7807 de ResponseStatusException vem dos hooks protegidos sobrescritos.
     */

    /** 4xx/5xx programaticos dos controllers → ProblemDetail com status original. */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(
            org.springframework.web.ErrorResponseException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ex.getBody();
        pd.setType(java.net.URI.create("https://portabilidade.errors/" + status.value()));
        return new ResponseEntity<>(pd, headers, status);
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

    /**
     * MaxUploadSizeExceededException ja consta na mapping global de
     * ResponseEntityExceptionHandler (handleException generico). Anotar um
     * handler local com @ExceptionHandler para a mesma excecao causa erro de
     * ambiguidade no Spring 6.1 — por isso sobrescrevemos o hook protegido.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "arquivo enviado excede o limite configurado");
        pd.setTitle("Payload muito grande");
        pd.setType(java.net.URI.create("https://portabilidade.errors/payload-too-large"));
        return new ResponseEntity<>(pd, headers, status);
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
