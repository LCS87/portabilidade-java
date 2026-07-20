package com.portabilidade.application;

import com.portabilidade.application.config.AppPaths;
import com.portabilidade.infrastructure.file.*;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Orquestrador principal do processamento batch.
 *
 * Responsabilidades:
 *  1. Garantir criação das pastas necessárias (entrada/, saida/).
 *  2. Escanear a pasta entrada/ em busca de arquivos .csv e .xlsx.
 *  3. Para cada arquivo: ler → processar → enriquecer → escrever saída.
 *  4. Tratar erros por arquivo de forma isolada (erro em um não impede os demais).
 */
public class PortabilityBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(PortabilityBatchProcessor.class);

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

    /**
     * Executa o processamento batch completo.
     */
    public void run() {
        log.info("=== Portabilidade Numérica Batch - Iniciando ===");

        // 1. Garantir que as pastas necessárias existam
        ensureDirectories();

        // 2. Escanear arquivos de entrada
        List<String> inputFiles = fileScanner.scan(paths.getInputDir());
        if (inputFiles.isEmpty()) {
            log.warn("Nenhum arquivo encontrado em '{}'. Encerrando.", paths.getInputDir());
            return;
        }

        int success = 0, failed = 0;

        // 3. Processar cada arquivo individualmente
        for (String filePath : inputFiles) {
            try {
                processFile(filePath);
                success++;
            } catch (Exception e) {
                log.error("Erro ao processar arquivo '{}': {} — pulando para o próximo.",
                        filePath, e.getMessage(), e);
                failed++;
            }
        }

        log.info("=== Processamento concluído: {} OK, {} com erro ===", success, failed);
    }

    private void processFile(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString();
        log.info("--- Processando: {} ---", fileName);

        // 4. Selecionar o leitor adequado (CSV ou Excel)
        InputFileReader reader = fileReaders.stream()
                .filter(r -> r.supports(filePath))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nenhum leitor para: " + fileName));

        // 5. Ler o arquivo de entrada
        List<Map<String, String>> inputRows = reader.read(filePath);
        if (inputRows.isEmpty()) {
            log.warn("Arquivo '{}' está vazio — ignorando.", fileName);
            return;
        }

        // 6. Extrair lista de telefones (coluna "telefone")
        boolean hasPhoneColumn = inputRows.get(0).containsKey("telefone");
        if (!hasPhoneColumn) {
            log.warn("Arquivo '{}' não possui coluna 'telefone' — ignorando.", fileName);
            return;
        }

        List<String> phones = inputRows.stream()
                .map(row -> row.getOrDefault("telefone", ""))
                .toList();

        // 7. Processar números via serviço de resolução
        ProcessResult result = resolutionService.processNumbers(phones);

        // 8. Enriquecer as linhas originais com operadora e fonte_consulta
        List<Map<String, Object>> outputRows = new ArrayList<>(inputRows.size());
        for (Map<String, String> inputRow : inputRows) {
            Map<String, Object> outputRow = new LinkedHashMap<>(inputRow);
            String phone = inputRow.getOrDefault("telefone", "");
            outputRow.put("operadora", result.operatorMap().getOrDefault(phone, "nao_identificado"));
            outputRow.put("fonte_consulta", result.sourceMap().getOrDefault(phone, "nao_identificado"));
            outputRows.add(outputRow);
        }

        // 9. Escrever arquivo de saída
        String baseName = fileName.replaceAll("(?i)\\.(csv|xlsx)$", "");
        String outputFile = outputWriter.write(outputRows, baseName, paths.getOutputDir());
        if (outputFile != null) {
            log.info("Saída gerada: {}", outputFile);
        }
    }

    private void ensureDirectories() {
        for (String dir : new String[]{paths.getInputDir(), paths.getOutputDir()}) {
            try {
                Files.createDirectories(Path.of(dir));
                log.debug("Diretório garantido: {}", dir);
            } catch (Exception e) {
                log.warn("Não foi possível criar diretório '{}': {}", dir, e.getMessage());
            }
        }
    }
}
