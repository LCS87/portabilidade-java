package com.portabilidade.infrastructure.batch;

import com.portabilidade.infrastructure.file.InputFileReader;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.ProcessResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 4: pipeline de execução de batch extraído do InMemoryBatchJobStore
 * para ser reaproveitado tanto pelo executor em memória (virtual threads)
 * quanto pelo listener da fila RabbitMQ — mesma semântica, transporte diferente.
 */
@Service
public class BatchExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BatchExecutionService.class);
    static final String PHONE_COLUMN = "telefone";
    static final String OPERATOR_COLUMN = "operadora";
    static final String SOURCE_COLUMN = "fonte_consulta";

    private final List<InputFileReader> readers;
    private final PortabilityResolutionService resolutionService;
    private final MeterRegistry meterRegistry;
    private final Path outputDir;
    private final int chunkSize;
    private final long maxRows;

    public BatchExecutionService(List<InputFileReader> readers,
                                 PortabilityResolutionService resolutionService,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider,
                                 @Value("${app.batch.output-dir:target/api-output}") String outputDir,
                                 @Value("${app.batch.chunk-size:50000}") int chunkSize,
                                 @Value("${app.batch.max-rows:1000000}") long maxRows) {
        this.readers = readers;
        this.resolutionService = resolutionService;
        // Optional de propósito: testes unitários podem montar o serviço sem actuator
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.outputDir = Path.of(outputDir);
        this.chunkSize = Math.max(1000, chunkSize);
        this.maxRows = maxRows;
    }

    /** Processa o arquivo associado ao job até a conclusão (ou falha isolada). */
    public void execute(BatchJob job, Path file) {
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
            // UTF-8 sem BOM + vírgula: consumo programático/download pela API
            out = new CsvOutputWriter.StreamingCsvWriter(
                    outputDir.resolve("batch_" + job.getId() + ".csv"), false, ',');
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
        // Fase 4: métrica de negócio — chunks e linhas processadas por worker
        if (meterRegistry != null) {
            meterRegistry.counter("portability_batch_rows_processed_total").increment(chunk.size());
        }
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
}
