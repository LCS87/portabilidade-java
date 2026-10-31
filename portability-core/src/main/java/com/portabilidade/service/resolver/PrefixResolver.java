package com.portabilidade.service.resolver;

/**
 * Port (interface) para resolução de operadora via prefixo do número.
 * Implementado por JsonPrefixResolver.
 */
public interface PrefixResolver {

    /**
     * Tenta identificar a operadora pelo prefixo dos dígitos limpos.
     *
     * @param cleanDigits string com 11 dígitos (DDD + número com 9)
     * @return nome da operadora ou null se não identificado
     */
    String resolve(String cleanDigits);
}
