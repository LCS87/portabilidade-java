package com.portabilidade.service.resolver;

import java.util.List;
import java.util.Map;

/**
 * Port (interface) para consulta de portabilidade numérica.
 * Implementado por DuckDbPortabilityRepository.
 *
 * <p>Extends {@link AutoCloseable}: implementações que mantêm recursos caros
 * (ex.: conexão única por execução no adapter DuckDB — Fase 1 do PLANO_MELHORIAS)
 * devem liberá-los em {@link #close()}. O contrato default não obriga nada.</p>
 */
public interface PortabilityRepository extends AutoCloseable {

    /**
     * Busca as operadoras (código RN1) para uma lista de números normalizados.
     *
     * @param numbers lista de números Long (11 dígitos)
     * @return mapa de telefone → rn1; só contém entradas para números encontrados
     */
    Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers);

    /** Libera recursos do adapter. Não checked — chamadores não precisam de try-with-resources obrigatório. */
    @Override
    default void close() {
        // no-op por padrão
    }
}
