package com.portabilidade.infrastructure.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
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
 *
 * Fase 1 (PLANO_MELHORIAS): escrita incremental via {@link #open} — os dados
 * são gravados conforme chegam (streaming), sem bufferizar todas as linhas.
 * BufferedWriter com buffer aumentado (1 MiB) reduz syscalls.
 */
public class CsvOutputWriter {

    private static final Logger log = LoggerFactory.getLogger(CsvOutputWriter.class);
    private static final char SEPARATOR = ';';
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private static final int WRITE_BUFFER_BYTES = 1024 * 1024;

    /**
     * Escreve o resultado em CSV (API legada, lista completa em memória).
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
        StreamingCsvWriter w = null;
        try {
            w = open(originalName, outputDir);
            for (Map<String, Object> row : rows) {
                w.writeRow(row);
            }
        } catch (IOException e) {
            log.error("Erro ao escrever arquivo de saída: {}", e.getMessage());
            return null;
        } finally {
            try { if (w != null) w.close(); } catch (IOException ignored) { }
        }
        String path = w.path();
        log.info("Arquivo gerado: {} ({} linhas)", path, w.rowsWritten());
        return path;
    }

    /**
     * Abre um writer incremental para um novo arquivo FINAL_* na pasta de saída.
     * O header é derivado da primeira linha escrita. Fechar o writer conclui o
     * arquivo (mesmo que nenhuma linha tenha sido escrita — nesse caso o
     * arquivo vazio resultante deve ser descartado pelo chamador via abort()).
     */
    public StreamingCsvWriter open(String originalName, String outputDir) throws IOException {
        Files.createDirectories(Path.of(outputDir));
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String baseName = originalName.replaceAll("(?i)\\.(csv|xlsx)$", "");
        String fileName = "FINAL_" + timestamp + "_" + baseName + ".csv";
        Path outputPath = Path.of(outputDir, fileName);
        return new StreamingCsvWriter(outputPath);
    }

    /** Writer de CSV em streaming: grava linha a linha, header automático. */
    public static final class StreamingCsvWriter implements Closeable {
        private final Path outputPath;
        private final BufferedWriter writer;
        private String[] headers;          // definidos na primeira linha
        private long rowsWritten;
        private boolean closed;

        StreamingCsvWriter(Path outputPath) throws IOException {
            this.outputPath = outputPath;
            // UTF-8 com BOM para compatibilidade com Excel
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputPath.toFile()),
                            StandardCharsets.UTF_8), WRITE_BUFFER_BYTES);
            this.writer.write('\uFEFF');
        }

        public void writeRow(Map<String, Object> row) throws IOException {
            if (headers == null) {
                headers = row.keySet().toArray(new String[0]);
                StringBuilder hl = new StringBuilder();
                for (int i = 0; i < headers.length; i++) {
                    if (i > 0) hl.append(SEPARATOR);
                    hl.append(escapeField(headers[i]));
                }
                writer.write(hl.toString());
                writer.newLine();
            }
            StringBuilder line = new StringBuilder();
            int i = 0;
            for (String h : headers) {
                if (i++ > 0) line.append(SEPARATOR);
                Object v = row.get(h);
                line.append(escapeField(v == null ? "" : v.toString()));
            }
            writer.write(line.toString());
            writer.newLine();
            rowsWritten++;
        }

        /** Caminho do arquivo de saída. */
        public String path() { return outputPath.toString(); }

        /** Total de linhas de dados escritas. */
        public long rowsWritten() { return rowsWritten; }

        /** Fecha e retorna o caminho gravado. */
        public String finish() throws IOException {
            close();
            return outputPath.toString();
        }

        /** Fecha e remove o arquivo (usado quando não há linhas a manter). */
        public void abort() {
            try { writer.close(); } catch (IOException ignored) { }
            closed = true;
            try { Files.deleteIfExists(outputPath); } catch (IOException ignored) { }
        }

        @Override
        public void close() throws IOException {
            if (!closed) writer.close();
        }
    }

    /**
     * Escapa um campo CSV: se contiver ; ou " ou quebra de linha, envolve em aspas.
     */
    private static String escapeField(String value) {
        if (value == null) return "";
        if (value.indexOf(SEPARATOR) >= 0 || value.indexOf('"') >= 0 || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
