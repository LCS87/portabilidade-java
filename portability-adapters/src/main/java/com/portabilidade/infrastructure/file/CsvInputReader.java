package com.portabilidade.infrastructure.file;

import com.portabilidade.domain.exception.DataLoadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Lê arquivos CSV de entrada.
 * Detecta automaticamente o separador (; ou ,) e o charset (UTF-8 ou ISO-8859-1).
 * Normaliza os headers para lowercase sem espaços extras.
 * Garante que exista uma coluna "telefone" (tenta mapear variantes comuns).
 *
 * Fase 1: {@link #stream(String)} oferece leitura linha-a-linha (iterator)
 * sem materializar o arquivo inteiro em memória.
 */
public class CsvInputReader implements InputFileReader {

    private static final Logger log = LoggerFactory.getLogger(CsvInputReader.class);

    // Variantes de nome de coluna que serão mapeadas para "telefone"
    private static final List<String> PHONE_COLUMN_ALIASES = List.of(
            "telefone", "fone", "celular", "numero", "número", "tel",
            "phone", "mobile", "msisdn", "ddd_numero", "nr_telefone"
    );

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".csv");
    }

    @Override
    public List<Map<String, String>> read(String filePath) {
        // Compatibilidade: materializa o stream em lista.
        List<Map<String, String>> rows = new ArrayList<>();
        Iterator<Map<String, String>> it = stream(filePath);
        while (it.hasNext()) rows.add(it.next());
        log.info("CSV lido: {} linhas de '{}'", rows.size(), Path.of(filePath).getFileName());
        return rows;
    }

    @Override
    public Iterator<Map<String, String>> stream(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new DataLoadingException("Arquivo não encontrado: " + filePath);
        }

        // Tenta UTF-8 primeiro, depois ISO-8859-1 — a detecção é feita lendo o
        // header; o iterator resultante continua no mesmo charset escolhido.
        Exception last = null;
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, Charset.forName("ISO-8859-1")}) {
            try {
                BufferedReader reader = Files.newBufferedReader(path, charset);
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    reader.close();
                    return Collections.emptyIterator();
                }
                return new CsvIterator(reader, headerLine, charset, path);
            } catch (Exception e) {
                last = e;
                log.debug("Falha ao iniciar leitura CSV com charset {}: {}", charset, e.getMessage());
            }
        }
        throw new DataLoadingException("Não foi possível ler o CSV: " + filePath, last);
    }

    /** Iterator streaming: mantém apenas a linha corrente em memória. */
    private final class CsvIterator implements Iterator<Map<String, String>> {
        private final BufferedReader reader;
        private final String[] headers;
        private final char sep;
        private final Path path;
        private Map<String, String> next;

        CsvIterator(BufferedReader reader, String headerLine, Charset charset, Path path) {
            this.reader = reader;
            this.path = path;
            String hl = headerLine.startsWith("\uFEFF") ? headerLine.substring(1) : headerLine;
            this.sep = detectSeparator(hl);
            this.headers = normalizeHeaders(hl.split(String.valueOf(sep), -1));
            advance();
        }

        private void advance() {
            next = null;
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] cols = splitLine(line, sep);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        String val = (i < cols.length) ? cols[i].trim().replaceAll("^['\"]|['\"]$", "") : "";
                        row.put(headers[i], val);
                    }
                    next = row;
                    return;
                }
                // fim do arquivo — libera o recurso
                reader.close();
            } catch (IOException e) {
                closeQuietly();
                throw new UncheckedIOException("Erro ao fazer streaming do CSV: " + path, e);
            }
        }

        @Override public boolean hasNext() { return next != null; }

        @Override
        public Map<String, String> next() {
            if (next == null) throw new NoSuchElementException();
            Map<String, String> cur = next;
            advance();
            return cur;
        }

        private void closeQuietly() {
            try { reader.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Normaliza os headers: lowercase, trim, remove aspas,
     * e mapeia aliases de coluna telefone para "telefone".
     */
    private String[] normalizeHeaders(String[] raw) {
        String[] result = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String h = raw[i].trim().toLowerCase()
                    .replaceAll("[\"']", "")
                    .replaceAll("\\s+", "_");
            // Mapeia aliases para "telefone"
            for (String alias : PHONE_COLUMN_ALIASES) {
                if (h.equals(alias) || h.contains(alias)) {
                    h = "telefone";
                    break;
                }
            }
            result[i] = h.isEmpty() ? "col_" + i : h;
        }
        return result;
    }

    private String[] splitLine(String line, char sep) {
        // Split simples, suficiente para CSVs sem aspas envolvendo separadores
        return line.split(String.valueOf(sep), -1);
    }

    private char detectSeparator(String firstLine) {
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        return (semicolons >= commas) ? ';' : ',';
    }
}
