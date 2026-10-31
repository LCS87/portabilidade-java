package com.portabilidade.infrastructure.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.portabilidade.service.resolver.PortabilityRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorator de {@link PortabilityRepository} com cache L1 em memória (Caffeine).
 *
 * Resultados de portabilidade são relativamente estáveis (mudam apenas quando a
 * base ANS é atualizada), então um TTL de horas é seguro e derruba a latência
 * p95 de consultas repetidas. A query real vai apenas aos números não-cached —
 * exatamente o que a Fase 2 do PLANO_MELHORIAS pede.
 *
 * O cache armazena também os "misses" (sentinela vazia = confirmado como sem
 * portabilidade até o TTL), evitando reconsultar o DuckDB para hot numbers
 * que nunca têm registro.
 */
public class CachedPortabilityRepository implements PortabilityRepository {

    /** Sentinela: número consultado e SEM portabilidade registrada. */
    private static final String MISS = "";

    private final PortabilityRepository delegate;
    private final Cache<Long, String> cache;

    public CachedPortabilityRepository(PortabilityRepository delegate, Cache<Long, String> cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers) {
        Map<Long, String> result = new HashMap<>();
        List<Long> uncached = new ArrayList<>();

        for (Long n : numbers) {
            String cached = cache.getIfPresent(n);
            if (cached != null) {
                if (!MISS.equals(cached)) {
                    result.put(n, cached);
                }
                // MISS sentinela: confirmado sem portabilidade — não reconsulta
            } else {
                uncached.add(n);
            }
        }

        if (!uncached.isEmpty()) {
            Map<Long, String> fresh = delegate.findOperatorsByPhoneNumbers(uncached);
            for (Long n : uncached) {
                String rn1 = fresh.get(n);
                cache.put(n, rn1 == null ? MISS : rn1);
                if (rn1 != null) {
                    result.put(n, rn1);
                }
            }
        }
        return result;
    }

    @Override
    public void close() {
        cache.invalidateAll();
        delegate.close();
    }
}
