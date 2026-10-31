package com.portabilidade.infrastructure.batch;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import com.portabilidade.infrastructure.file.InputFileReader;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.ProcessResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementação dos ports BatchExecutor/JobRegistry (Fase 2).
 *
 * Jobs em memória + execução em virtual threads (Java 21) reaproveitando o
 * pipeline streaming da Fase 1: leitura incremental por chunks → resolução →
 * escrita incremental com flush a cada chunk (progresso observável via GET).
 *
 * Fase 4 substituirá este store por fila durável (RabbitMQ/Kafka) sem alterar
 * o use case — é exatamente para isso que os ports existem.
 */
@Component
public class InMemoryBatchJobStore
        implements SubmitBatchUseCase.BatchExecutor, SubmitBatchUseCase.JobRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBatchJobStore.class);
    static final String PHONE_COLUMN = "telefone";
    static final String OPERATOR_COLUMN = "operadora";
    static final String SOURCE_COLUMN = "fonte_consulta";

    private final Map<String, BatchJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final List<InputFileReader> readers;
    private final PortabilityResolutionService resolutionService;
    private final Path outputDir;
    private final int chunkSize;
    private final long maxRows;

    public InMemoryBatchJobStore(List<InputFileReader> readers,
                                 PortabilityResolutionService resolutionService,
                                 @Value("${app.batch.output-dir:target/api-output}") String outputDir,
                                 @Value("${app.batch.chunk-size:50000}") int chunkSize,
                                 @Value("${app.batch.max-rows:1000000}") long maxRows) {
        this.readers = readers;
        this.resolutionService = resolutionService;
        this.outputDir = Path.of(outputDir);
        this.chunkSize = Math.max(1000, chunkSize);
        this.maxRows = maxRows;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public String submit(Path savedFile, String originalFileName) {
        String id = UUID.randomUUID().toString();
        BatchJob job = new BatchJob(id, originalFileName);
        jobs.put(id, job);
        executor.submit(() -> run(job, savedFile));
        return id;
    }

    @Override
    public Optional<BatchJob> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    // ── execução ────────────────────────────────────────────────────────────

    private void run(BatchJob job, Path file) {
        job.setStatus(BatchJob.Status.RUNNING);
        InputFileReader reader = readers.stream()
                .filter(r -> r.supports(file.toString()))
                .findFirst().orElse(null);
        if (reader == null) {
            fail(job, "formato nao suportado: envie .csv ou .xlsx");
            quietDelete(file);
            return;
        }
        CsvOutputWriter.StreamingCsvWriter out = null;
        try {
            Files.createDirectories(outputDir);
            // UTF-8 sem BOM: consumo programático/download pela API
            out = new CsvOutputWriter.StreamingCsvWriter(
                    outputDir.resolve("batch_" + job.getId() + ".csv"), false);
            Iterator<Map<String, String>> it = reader.stream(file.toString());
            List<String> buffer = new ArrayList<>(chunkSize);
            boolean headerWritten = false;
            long total = 0;
            while (it.hasNext()) {
                Map<String, String> row = it.next();
                if (!headerWritten) {
                    if (row.get(PHONE_COLUMN) == null) {
                        fail(job, "coluna 'telefone' obrigatoria nao encontrada no arquivo");
                        out.abort();
                        quietDelete(file);
                        return;
                    }
                    out.writeHeader(List.of(PHONE_COLUMN, OPERATOR_COLUMN, SOURCE_COLUMN));
                    headerWritten = true;
                }
                if (++total > maxRows) {
                    fail(job, "limite de " + maxRows + " linhas excedido");
                    out.abort();
                    quietDelete(file);
                    return;
                }
                buffer.add(row.get(PHONE_COLUMN));
                if (buffer.size() >= chunkSize) {
                    processChunk(buffer, out, job);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                processChunk(buffer, out, job);
            }
            job.setTotalRows(total);
            job.setResultPath(out.finish());
            job.setStatus(BatchJob.Status.COMPLETED);
            log.info("Job {} concluido: {} linhas", job.getId(), total);
        } catch (Exception e) {
            if (out != null) {
                out.abort();
            }
            fail(job, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.error("Job {} falhou", job.getId(), e);
        } finally {
            quietDelete(file);
        }
    }

    private void processChunk(List<String> chunk,
                              CsvOutputWriter.StreamingCsvWriter out,
                              BatchJob job) throws IOException {
        ProcessResult result = resolutionService.processNumbers(new ArrayList<>(chunk));
        for (int i = 0; i < result.size(); i++) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put(PHONE_COLUMN, result.rawNumbers().get(i));
            line.put(OPERATOR_COLUMN, result.operatorAt(i));
            line.put(SOURCE_COLUMN, result.sourceAt(i));
            out.writeRow(line);
        }
        out.flush();
        job.addProcessed(result.size());
    }

    private static void fail(BatchJob job, String message) {
        job.setErrorMessage(message);
        job.setStatus(BatchJob.Status.FAILED);
    }

    private static void quietDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
