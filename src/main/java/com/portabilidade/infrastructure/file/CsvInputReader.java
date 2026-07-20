package com.portabilidade.infrastructure.file;

import com.portabilidade.domain.exception.DataLoadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
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
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new DataLoadingException("Arquivo não encontrado: " + filePath);
        }

        // Tenta UTF-8 primeiro, depois ISO-8859-1
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, Charset.forName("ISO-8859-1")}) {
            try {
                List<Map<String, String>> result = doRead(path, charset);
                log.info("CSV lido: {} linhas de '{}' (charset: {})", result.size(),
                        path.getFileName(), charset);
                return result;
            } catch (DataLoadingException e) {
                throw e;
            } catch (Exception e) {
                log.debug("Falha ao ler CSV com charset {}: {}", charset, e.getMessage());
            }
        }
        throw new DataLoadingException("Não foi possível ler o CSV: " + filePath);
    }

    private List<Map<String, String>> doRead(Path path, Charset charset) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return Collections.emptyList();

            // Remove BOM
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            char sep = detectSeparator(headerLine);
            String[] rawHeaders = headerLine.split(String.valueOf(sep), -1);
            String[] headers = normalizeHeaders(rawHeaders);

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = splitLine(line, sep);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String val = (i < cols.length) ? cols[i].trim().replaceAll("^[\"']|[\"']$", "") : "";
                    row.put(headers[i], val);
                }
                rows.add(row);
            }
            return rows;
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
