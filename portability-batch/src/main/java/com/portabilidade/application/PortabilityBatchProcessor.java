package com.portabilidade.application;

import com.portabilidade.application.config.AppPaths;
import com.portabilidade.infrastructure.file.FileScanner;
import com.portabilidade.infrastructure.file.InputFileReader;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Orquestrador principal do processamento batch.
 * Coordena: scanner → leitura (streaming) → normalização → resolução → escrita incremental.
 *
 * Fase 1 (PLANO_MELHORIAS): fluxo 100% streaming — linhas são lidas, resolvidas
 * em lotes deduplicados e gravadas conforme chegam; nada além de um lote fica
 * em memória. A conexão DuckDB é única por execução e liberada no finally.
 */
public class PortabilityBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(PortabilityBatchProcessor.class);

    /** Tamanho do lote de resolução em streaming (linhas lidas por chamada ao service). */
    static final int STREAM_CHUNK_SIZE = 50_000;

    private final AppPaths paths;
    private final PortabilityResolutionService resolutionService;
    private final FileScanner fileScanner;
    private final List<InputFileReader> fileReaders;
    private final CsvOutputWriter outputWriter;

    public PortabilityBatchProcessor(AppPaths paths,
                                     PortabilityResolutionService resolutionService,
                                     FileScanner fileScanner,
                                     List<InputFileReader> fileReaders,
                                     CsvOutputWriter outputWriter) {
        this.paths = paths;
        this.resolutionService = resolutionService;
        this.fileScanner = fileScanner;
        this.fileReaders = fileReaders;
        this.outputWriter = outputWriter;
    }

    /** Executa o pipeline completo sobre todos os arquivos na pasta de entrada. */
    public void run() {
        long start = System.currentTimeMillis();
        log.info("Iniciando processamento batch");

        try {
            // 1. Obter lista de arquivos da pasta de entrada
            List<String> inputFiles = fileScanner.scan(paths.getInputDir());
            if (inputFiles.isEmpty()) {
                log.warn("Nenhum arquivo encontrado na pasta de entrada: {}", paths.getInputDir());
                return;
            }
            log.info("{} arquivo(s) encontrados", inputFiles.size());

            // 2. Processar cada arquivo individualmente com isolamento de erros:
            // falha em um arquivo (leitor ausente, formato inválido) não derruba a execução
            for (String filePath : inputFiles) {
                try {
                    processFile(filePath);
                } catch (Exception e) {
                    log.error("Falha ao processar '{}': {}", filePath, e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("Processamento concluído em {} ms", elapsed);
        } finally {
            // Conexão única do DuckDB liberada ao fim da execução
            resolutionService.close();
        }
    }

    /**
     * Processa um único arquivo de entrada em streaming: lê chunks de linhas,
     * resolve e grava incrementalmente sem materializar o arquivo inteiro.
     */
    public void processFile(String filePath) {
        log.info("Processando: {}", filePath);
        String fileName = Path.of(filePath).getFileName().toString();

        InputFileReader reader = fileReaders.stream()
                .filter(r -> r.supports(filePath))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhum leitor disponível para o arquivo: " + filePath));

        Iterator<Map<String, String>> source = reader.stream(filePath);
        if (!source.hasNext()) {
            log.warn("Arquivo vazio ou sem dados: {}", fileName);
            return;
        }

        // Fase 1: peek na primeira linha para validar a coluna "telefone" antes
        // de criar qualquer arquivo de saída (arquivos sem a coluna são ignorados).
        Map<String, String> firstRow = source.next();
        if (firstRow.get("telefone") == null) {
            log.warn("Coluna 'telefone' não encontrada em '{}'; arquivo ignorado", fileName);
            return;
        }

        try {
            CsvOutputWriter.StreamingCsvWriter out =
                    outputWriter.open(Path.of(filePath).getFileName().toString(), paths.getOutputDir());
            boolean ok = false;
            try {
                List<Map<String, String>> chunk = new ArrayList<>(STREAM_CHUNK_SIZE);
                chunk.add(firstRow);
                while (source.hasNext()) {
                    chunk.add(source.next());
                    if (chunk.size() >= STREAM_CHUNK_SIZE) {
                        writeChunk(out, chunk);
                        chunk.clear();
                    }
                }
                if (!chunk.isEmpty()) {
                    writeChunk(out, chunk);
                    chunk.clear();
                }
                ok = out.rowsWritten() > 0;
            } finally {
                if (ok) out.finish(); else out.abort();
            }
            if (ok) {
                log.info("Arquivo gerado: {} ({} linhas)", out.path(), out.rowsWritten());
            } else {
                log.warn("Nenhuma linha válida em '{}'; saída descartada", fileName);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Erro ao processar arquivo: " + filePath, e);
        }
    }

    /** Resolve um chunk e escreve as linhas enriquecidas (ordem preservada). */
    private void writeChunk(CsvOutputWriter.StreamingCsvWriter out,
                            List<Map<String, String>> chunk) throws IOException {
        List<String> rawNumbers = extractPhoneColumn(chunk);
        ProcessResult result = resolutionService.processNumbers(rawNumbers);

        List<Map<String, Object>> enriched = enrichRowsByPosition(chunk, result);
        for (Map<String, Object> row : enriched) {
            out.writeRow(row);
        }
    }

    /** Extrai a coluna "telefone" de cada linha (null se ausente). */
    private List<String> extractPhoneColumn(List<Map<String, String>> rows) {
        List<String> numbers = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            numbers.add(row.get("telefone"));
        }
        return numbers;
    }

    /** Enriquecimento por posição: mantém todas as colunas originais na ordem original. */
    private List<Map<String, Object>> enrichRowsByPosition(List<Map<String, String>> inputRows,
                                                           ProcessResult result) {
        List<Map<String, Object>> enriched = new ArrayList<>(inputRows.size());
        for (int i = 0; i < inputRows.size(); i++) {
            Map<String, Object> newRow = new LinkedHashMap<>(inputRows.get(i));
            newRow.put("operadora", result.operatorAt(i));
            newRow.put("fonte_consulta", result.sourceAt(i));
            enriched.add(newRow);
        }
        return enriched;
    }
}
