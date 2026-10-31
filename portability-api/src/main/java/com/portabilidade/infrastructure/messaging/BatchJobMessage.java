package com.portabilidade.infrastructure.messaging;

/** Payload durável de um job de batch na fila (identifica o arquivo persistido). */
public record BatchJobMessage(String jobId, String filePath, String originalFileName) {
    public BatchJobMessage { }
}
