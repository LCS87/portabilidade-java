package com.portabilidade.infrastructure.loader;

import com.portabilidade.domain.exception.DataLoadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Carrega o arquivo Operadoras.csv e retorna um mapa de RN1 → Nome da Operadora.
 *
 * O arquivo pode usar separador ; ou , e pode estar em UTF-8 ou ISO-8859-1.
 * Detecta automaticamente o separador lendo a primeira linha.
 *
 * Colunas esperadas (case-insensitive): "rn1" (ou "codigo_rn1") e "nome" (ou "razao_social").
 */
public class CsvOperatorLoader {

    private static final Logger log = LoggerFactory.getLogger(CsvOperatorLoader.class);

    /**
     * Carrega operadoras do arquivo CSV.
     *
     * @param csvPath caminho para Operadoras.csv
     * @return mapa RN1 → nome da operadora (nunca null, pode ser vazio)
     */
    public Map<String, String> load(String csvPath) {
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            log.warn("Arquivo Operadoras.csv não encontrado em: {}", csvPath);
            return Collections.emptyMap();
        }

        // Tenta UTF-8 primeiro, depois ISO-8859-1
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, Charset.forName("ISO-8859-1")}) {
            try {
                Map<String, String> result = doLoad(path, charset);
                if (!result.isEmpty()) {
                    log.info("Operadoras.csv carregado: {} entradas (charset: {})", result.size(), charset);
                    return result;
                }
            } catch (Exception e) {
                log.debug("Falha ao ler com charset {}: {}", charset, e.getMessage());
            }
        }
        log.warn("Operadoras.csv carregado vazio ou com erro.");
        return Collections.emptyMap();
    }

    private Map<String, String> doLoad(Path path, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return Collections.emptyMap();

            // Remove BOM se presente
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            char separator = detectSeparator(headerLine);
            String[] headers = headerLine.split(String.valueOf(separator), -1);

            // Encontrar índices das colunas rn1 e nome (tolerante a prefixos como "# Nome")
            int rn1Idx = -1, nomeIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase().replaceAll("[\"']", "");
                h = h.replaceAll("^#\\s*", ""); // remove "# " inicial
                if (h.equals("rn1") || h.equals("codigo_rn1") || h.contains("rn1")) {
                    rn1Idx = i;
                } else if (h.equals("nome") || h.equals("razao_social") || h.contains("nome")) {
                    nomeIdx = i;
                }
            }

            if (rn1Idx == -1 || nomeIdx == -1) {
                log.warn("Colunas rn1/nome não encontradas no Operadoras.csv. Headers: {}",
                        Arrays.toString(headers));
                return Collections.emptyMap();
            }

            Map<String, String> result = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = line.split(String.valueOf(separator), -1);
                if (cols.length <= Math.max(rn1Idx, nomeIdx)) continue;

                String rn1 = cols[rn1Idx].trim().replaceAll("[\"']", "");
                String nome = cols[nomeIdx].trim().replaceAll("[\"']", "");
                if (!rn1.isBlank() && !nome.isBlank()) {
                    // RN1 na base é BIGINT (sem zeros à esquerda); normaliza para evitar
                    // chaves divergentes como "055321" vs "55321".
                    try {
                        rn1 = String.valueOf(Long.parseLong(rn1));
                    } catch (NumberFormatException ignored) { /* mantém original */ }
                    result.put(rn1, nome.toUpperCase());
                }
            }
            return result;
        }
    }

    private char detectSeparator(String firstLine) {
        // Conta ocorrências de ; e , para decidir
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        return semicolons >= commas ? ';' : ',';
    }
}
