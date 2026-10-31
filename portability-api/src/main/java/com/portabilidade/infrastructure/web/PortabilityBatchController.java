package com.portabilidade.infrastructure.web;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import com.portabilidade.infrastructure.batch.BatchJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de entrada REST para batch assíncrono (Fase 2).
 * POST multipart → 202 + job id; GET status/progresso; GET download do CSV.
 */
@RestController
@RequestMapping("/api/v1/portabilidade/batch")
@Tag(name = "Portabilidade Batch", description = "Processamento assíncrono de arquivos CSV/XLSX")
public class PortabilityBatchController {

    private static final String DOWNLOAD_NAME = "resultado_portabilidade.csv";

    private final SubmitBatchUseCase submitBatchUseCase;
    private final Path uploadDir;

    public PortabilityBatchController(SubmitBatchUseCase submitBatchUseCase,
                                      @org.springframework.beans.factory.annotation.Value(
                                              "${app.batch.upload-dir:target/api-upload}") String uploadDir) {
        this.submitBatchUseCase = submitBatchUseCase;
        this.uploadDir = Path.of(uploadDir);
    }

    @Operation(summary = "Envia arquivo CSV/XLSX e retorna o id do job (202 Accepted)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submit(@RequestParam("arquivo") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "campo 'arquivo' vazio; envie multipart/form-data com um .csv ou .xlsx");
        }
        String originalName = sanitizeName(file.getOriginalFilename());
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "formato nao suportado: apenas .csv e .xlsx");
        }
        Path stored;
        try {
            Files.createDirectories(uploadDir);
            stored = uploadDir.resolve(UUID.randomUUID() + "-" + originalName);
            file.transferTo(stored);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "falha ao persistir upload: " + e.getMessage());
        }
        String jobId = submitBatchUseCase.submit(stored, originalName);
        return ResponseEntity.accepted().body(Map.of(
                "id", jobId,
                "arquivo", originalName,
                "status", "PENDING",
                "links", Map.of(
                        "status", "/api/v1/portabilidade/batch/" + jobId,
                        "resultado", "/api/v1/portabilidade/batch/" + jobId + "/resultado")));
    }

    @Operation(summary = "Status e progresso do job")
    @GetMapping("/{id}")
    public ResponseEntity<BatchStatusResponse> status(@PathVariable String id) {
        BatchJob job = findOr404(id);
        return ResponseEntity.ok(new BatchStatusResponse(
                job.getId(), job.getOriginalFileName(), job.getStatus().name(),
                job.getProcessedRows(), job.getTotalRows(), job.getCreatedAt(),
                job.getErrorMessage()));
    }

    @Operation(summary = "Download do CSV resultado (202 enquanto em execução)")
    @GetMapping("/{id}/resultado")
    public ResponseEntity<?> download(@PathVariable String id) {
        BatchJob job = findOr404(id);
        switch (job.getStatus()) {
            case PENDING, RUNNING -> {
                return ResponseEntity.accepted().body(Map.of(
                        "id", job.getId(),
                        "status", job.getStatus().name(),
                        "linhasProcessadas", job.getProcessedRows()));
            }
            case FAILED -> {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "id", job.getId(),
                        "status", "FAILED",
                        "erro", job.getErrorMessage() == null ? "falha desconhecida" : job.getErrorMessage()));
            }
            default -> {
                Path result = Path.of(job.getResultPath());
                if (!Files.exists(result)) {
                    throw new ResponseStatusException(HttpStatus.GONE, "resultado expirado");
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                        .header("Content-Disposition", "attachment; filename=\"" + DOWNLOAD_NAME + "\"")
                        .body(new FileSystemResource(result));
            }
        }
    }

    private BatchJob findOr404(String id) {
        return submitBatchUseCase.status(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "job nao encontrado: " + id));
    }

    /** Evita path traversal / nomes hostis vindos do client. */
    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "arquivo.csv";
        }
        String base = Path.of(name).getFileName().toString();
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
