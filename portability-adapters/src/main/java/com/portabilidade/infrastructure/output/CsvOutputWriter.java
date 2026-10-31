package com.portabilidade.infrastructure.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Escreve o resultado do processamento em um arquivo CSV de saída.
 *
 * Padrão do arquivo:
 *   - Encoding: UTF-8 com BOM (para compatibilidade com Excel)
 *   - Separador: ;
 *   - Nome: FINAL_yyyyMMdd_HHmm_{nomeOriginalSemExtensao}.csv
 *   - Colunas: todas as originais + operadora + fonte_consulta
 */
public class CsvOutputWriter {

    private static final Logger log = LoggerFactory.getLogger(CsvOutputWriter.class);
    private static final char SEPARATOR = ';';
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    /**
     * Escreve o resultado em CSV.
     *
     * @param rows          lista de linhas com todos os campos originais + operadora + fonte_consulta
     * @param originalName  nome do arquivo original (sem extensão), usado no nome do output
     * @param outputDir     diretório de saída
     * @return caminho do arquivo gerado
     */
    public String write(List<Map<String, Object>> rows, String originalName, String outputDir) {
        if (rows == null || rows.isEmpty()) {
            log.warn("Nenhuma linha para escrever no output de '{}'", originalName);
            return null;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        // Remove extensão se ainda presente no nome
        String baseName = originalName.replaceAll("(?i)\\.(csv|xlsx)$", "");
        String fileName = "FINAL_" + timestamp + "_" + baseName + ".csv";
        Path outputPath = Path.of(outputDir, fileName);

        try {
            Files.createDirectories(Path.of(outputDir));

            // UTF-8 BOM para compatibilidade com Excel
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(outputPath.toFile()), StandardCharsets.UTF_8))) {

                // BOM
                writer.write('\uFEFF');

                // Headers — pega do primeiro registro
                Map<String, Object> firstRow = rows.get(0);
                String headerLine = String.join(String.valueOf(SEPARATOR),
                        firstRow.keySet().stream()
                                .map(this::escapeField)
                                .toList());
                writer.write(headerLine);
                writer.newLine();

                // Dados
                for (Map<String, Object> row : rows) {
                    String dataLine = String.join(String.valueOf(SEPARATOR),
                            row.values().stream()
                                    .map(v -> escapeField(v == null ? "" : v.toString()))
                                    .toList());
                    writer.write(dataLine);
                    writer.newLine();
                }
            }

            log.info("Arquivo gerado: {} ({} linhas)", outputPath, rows.size());
            return outputPath.toString();

        } catch (IOException e) {
            log.error("Erro ao escrever arquivo de saída '{}': {}", outputPath, e.getMessage());
            return null;
        }
    }

    /**
     * Escapa um campo CSV: se contiver ; ou " ou quebra de linha, envolve em aspas.
     */
    private String escapeField(String value) {
        if (value == null) return "";
        if (value.contains(String.valueOf(SEPARATOR)) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
