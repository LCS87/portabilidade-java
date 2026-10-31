package com.portabilidade.service.resolver;

import java.util.List;
import java.util.Map;

/**
 * Port (interface) para consulta de portabilidade numérica.
 * Implementado por DuckDbPortabilityRepository.
 */
public interface PortabilityRepository {

    /**
     * Busca as operadoras (código RN1) para uma lista de números normalizados.
     *
     * @param numbers lista de números Long (11 dígitos)
     * @return mapa de telefone → rn1; só contém entradas para números encontrados
     */
    Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers);
}
