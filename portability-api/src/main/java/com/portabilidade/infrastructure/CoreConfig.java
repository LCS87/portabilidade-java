package com.portabilidade.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portabilidade.application.config.AppPaths;
import com.portabilidade.infrastructure.file.CsvInputReader;
import com.portabilidade.infrastructure.file.ExcelInputReader;
import com.portabilidade.infrastructure.file.InputFileReader;
import com.portabilidade.infrastructure.loader.CsvOperatorLoader;
import com.portabilidade.infrastructure.loader.JsonPrefixMapLoader;
import com.portabilidade.infrastructure.repository.CachedPortabilityRepository;
import com.portabilidade.infrastructure.repository.DuckDbPortabilityRepository;
import com.portabilidade.infrastructure.resolver.JsonPrefixResolver;
import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composition root da API (Fase 2): substitui a DI manual do main do batch por
 * @Configuration, mantendo o core 100% reutilizado (mesmos ports/adapters).
 */
@Configuration
public class CoreConfig {

    private static final Logger log = LoggerFactory.getLogger(CoreConfig.class);

    @Value("${app.data.base-dir:${user.dir}}")
    private String baseDir;

    @Value("${app.data.duckdb-path:#{null}}")
    private String duckdbPath;

    @Value("${app.data.operadores-csv:#{null}}")
    private String operadoresCsv;

    @Value("${app.data.operadores-csv-completo:#{null}}")
    private String operadoresCompletoCsv;

    @Value("${app.data.prefixos-json:#{null}}")
    private String prefixosJson;

    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.cache.ttl-minutes:360}")
    private long cacheTtlMinutes;

    @Value("${app.cache.max-size:500000}")
    private long cacheMaxSize;

    private DuckDbPortabilityRepository repositoryRef;

    private AppPaths paths() {
        return new AppPaths(baseDir);
    }

    private String resolve(String explicit, java.util.function.Function<AppPaths, String> fallback) {
        return explicit != null ? explicit : fallback.apply(paths());
    }

    // ── adaptadores de saída (ports do core) ────────────────────────────────

    /**
     * Port de consulta: DuckDB (adapter existente, conexão única — Fase 1)
     * decorado pelo cache L1 Caffeine quando habilitado. Resultado de
     * portabilidade é estável por horas → TTL alto é seguro e derruba a
     * latência de hot numbers para ~O(1) (meta p95 < 50 ms da Fase 2).
     */
    @Bean
    public PortabilityRepository portabilityRepository() {
        String db = resolve(duckdbPath, AppPaths::getDuckDbPath);
        log.info("API usando DuckDB em: {}", db);
        repositoryRef = new DuckDbPortabilityRepository(db);
        if (!cacheEnabled) {
            return repositoryRef;
        }
        Cache<Long, String> l1 = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .recordStats()
                .build();
        log.info("Cache L1 Caffeine ativo: maxSize={}, ttl={}min", cacheMaxSize, cacheTtlMinutes);
        return new CachedPortabilityRepository(repositoryRef, l1);
    }

    @Bean
    public PrefixResolver prefixResolver() {
        Map<String, String> prefixMap =
                new JsonPrefixMapLoader().load(resolve(prefixosJson, AppPaths::getPrefixosJson));
        log.info("Mapa de prefixos carregado: {} entradas", prefixMap.size());
        return new JsonPrefixResolver(prefixMap);
    }

    @Bean
    public Map<String, String> operatorsDict() {
        CsvOperatorLoader loader = new CsvOperatorLoader();
        Map<String, String> dict = new LinkedHashMap<>(
                loader.load(resolve(operadoresCsv, AppPaths::getOperadoresCsv)));
        String completo = operadoresCompletoCsv != null ? operadoresCompletoCsv : paths().getOperadoresCsvCompleto();
        if (completo != null) {
            Map<String, String> extra = loader.load(completo);
            if (!extra.isEmpty()) {
                dict = extra;
                log.info("Dicionario COMPLETO de operadoras: {} entradas ({})", dict.size(), completo);
            }
        }
        return dict;
    }

    // ── serviços do core ─────────────────────────────────────────────────────

    @Bean
    public PhoneNormalizer phoneNormalizer() {
        return new PhoneNormalizer();
    }

    @Bean
    public PortabilityResolutionService portabilityResolutionService(PortabilityRepository repository,
                                                                     PrefixResolver prefixResolver,
                                                                     PhoneNormalizer normalizer,
                                                                     Map<String, String> operatorsDict) {
        return new PortabilityResolutionService(repository, prefixResolver, normalizer, operatorsDict);
    }

    @Bean
    public List<InputFileReader> inputFileReaders() {
        return List.of(new CsvInputReader(), new ExcelInputReader());
    }

    @PreDestroy
    void shutdown() {
        if (repositoryRef != null) {
            repositoryRef.close();
            log.info("Conexao DuckDB da API encerrada");
        }
    }
}
