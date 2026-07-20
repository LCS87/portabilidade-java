package com.portabilidade.infrastructure.resolver;

import com.portabilidade.service.resolver.PrefixResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementação de PrefixResolver que usa o mapa carregado de prefixos_operadora.json.
 *
 * Estratégia de busca:
 *   O mapa de prefixos foi gerado a partir de números de 11 dígitos (DDD + 9 + 8).
 *   Para suportar também números de 10 dígitos (linhas fixas), testamos o prefixo
 *   tanto na forma de 11 dígitos quanto na forma "fixa" (removendo o 9 após o DDD).
 *   Em ambos os casos, testamos prefixos de 7, 6 e 5 dígitos.
 *   O primeiro prefixo encontrado determina a operadora.
 */
public class JsonPrefixResolver implements PrefixResolver {

    private static final Logger log = LoggerFactory.getLogger(JsonPrefixResolver.class);
    private static final int[] PREFIX_LENGTHS = {7, 6, 5};

    private final Map<String, String> prefixMap;

    public JsonPrefixResolver(Map<String, String> prefixMap) {
        this.prefixMap = prefixMap;
    }

    @Override
    public String resolve(String cleanDigits) {
        if (cleanDigits == null || cleanDigits.length() < 7) return null;

        // Forma canônica de 11 dígitos (celular) e, se for 10 dígitos (fixo),
        // também a forma "com 9" usada no mapa de prefixos.
        List<String> candidates = new ArrayList<>();
        candidates.add(cleanDigits);
        if (cleanDigits.length() == 10) {
            // fixo "11 3333 4444" -> "11 9 3333 4444" para casar com o mapa de prefixos
            candidates.add(cleanDigits.substring(0, 2) + "9" + cleanDigits.substring(2));
        }

        for (String candidate : candidates) {
            for (int len : PREFIX_LENGTHS) {
                if (candidate.length() >= len) {
                    String prefix = candidate.substring(0, len);
                    String operadora = prefixMap.get(prefix);
                    if (operadora != null) {
                        log.debug("Prefixo {} ({} dígitos) → {}", prefix, len, operadora);
                        return operadora;
                    }
                }
            }
        }

        return null;
    }
}
