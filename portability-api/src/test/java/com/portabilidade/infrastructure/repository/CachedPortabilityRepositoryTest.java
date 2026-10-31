package com.portabilidade.infrastructure.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portabilidade.service.resolver.PortabilityRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 2 (PLANO_MELHORIAS): contrato do cache L1 Caffeine.
 * - hit não reconsulta o delegate
 * - miss (sem portabilidade) é memoizado por sentinela e também não reconsulta
 * - números novos vão apenas uma vez ao delegate
 */
class CachedPortabilityRepositoryTest {

    private static Cache<Long, String> newCache() {
        return Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(5)).build();
    }

    /** Delegate fake: conta as consultas e devolve operadora para 11999999999. */
    private static final class FakeRepo implements PortabilityRepository {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers) {
            calls.incrementAndGet();
            return numbers.contains(11999999999L) ? Map.of(11999999999L, "VIVO") : Map.of();
        }
    }

    @Test
    void secondQueryIsServedFromCacheWithoutTouchingDelegate() {
        FakeRepo fake = new FakeRepo();
        CachedPortabilityRepository cached = new CachedPortabilityRepository(fake, newCache());

        Map<Long, String> first = cached.findOperatorsByPhoneNumbers(List.of(11999999999L));
        Map<Long, String> second = cached.findOperatorsByPhoneNumbers(List.of(11999999999L));

        assertEquals("VIVO", first.get(11999999999L));
        assertEquals("VIVO", second.get(11999999999L));
        assertEquals(1, fake.calls.get(), "segunda chamada nao deve ir ao delegate");
    }

    @Test
    void missIsMemoizedWithSentinelAndNotRequeried() {
        FakeRepo fake = new FakeRepo();
        CachedPortabilityRepository cached = new CachedPortabilityRepository(fake, newCache());

        Map<Long, String> r1 = cached.findOperatorsByPhoneNumbers(List.of(21777777777L));
        Map<Long, String> r2 = cached.findOperatorsByPhoneNumbers(List.of(21777777777L));

        assertTrue(r1.isEmpty());
        assertTrue(r2.isEmpty());
        assertEquals(1, fake.calls.get(), "numero sem portabilidade nao deve ser reconsultado");
    }

    @Test
    void onlyUncachedNumbersGoToTheDelegate() {
        FakeRepo fake = new FakeRepo();
        CachedPortabilityRepository cached = new CachedPortabilityRepository(fake, newCache());
        cached.findOperatorsByPhoneNumbers(List.of(11999999999L)); // já em cache

        Map<Long, String> mixed = cached.findOperatorsByPhoneNumbers(
                List.of(11999999999L, 31888888888L));

        assertEquals("VIVO", mixed.get(11999999999L));
        assertEquals(2, fake.calls.get());
        assertTrue(mixed.containsKey(11999999999L));
    }
}
