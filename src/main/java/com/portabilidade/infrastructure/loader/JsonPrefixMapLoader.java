package com.portabilidade.infrastructure.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portabilidade.domain.exception.DataLoadingException;
import com.portabilidade.domain.model.PrefixMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Carrega o arquivo prefixos_operadora.json e retorna um mapa de prefixo → nome da operadora.
 *
 * Formato do JSON:
 * {
 *   "1191000": { "operadora": "CLARO", "rn1": "55321", "qtd": 103 },
 *   "1191001": { ... }
 * }
 */
public class JsonPrefixMapLoader {

    private static final Logger log = LoggerFactory.getLogger(JsonPrefixMapLoader.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Carrega o arquivo JSON e retorna mapa de prefixo → nome da operadora.
     *
     * @param jsonPath caminho para prefixos_operadora.json
     * @return mapa prefixo (string) → nome operadora; vazio se não encontrado
     */
    public Map<String, String> load(String jsonPath) {
        File file = new File(jsonPath);
        if (!file.exists()) {
            log.warn("Arquivo de prefixos não encontrado em: {}", jsonPath);
            return Collections.emptyMap();
        }

        try {
            Map<String, PrefixMapping> raw = objectMapper.readValue(
                    file,
                    new TypeReference<Map<String, PrefixMapping>>() {}
            );

            Map<String, String> result = new HashMap<>(raw.size());
            for (Map.Entry<String, PrefixMapping> entry : raw.entrySet()) {
                String prefix = entry.getKey().trim();
                String operadora = entry.getValue().getOperadora();
                if (operadora != null && !operadora.isBlank()) {
                    result.put(prefix, operadora.toUpperCase().trim());
                }
            }

            log.info("prefixos_operadora.json carregado: {} prefixos", result.size());
            return result;

        } catch (Exception e) {
            throw new DataLoadingException("Falha ao carregar prefixos_operadora.json: " + jsonPath, e);
        }
    }
}
