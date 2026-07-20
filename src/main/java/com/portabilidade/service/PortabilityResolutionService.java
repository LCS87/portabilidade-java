package com.portabilidade.service;

import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Serviço principal de resolução de operadora por portabilidade numérica.
 *
 * Estratégia em duas etapas:
 *  1. Consulta DuckDB (portabilidade oficial)
 *  2. Fallback por prefixo JSON
 *
 * Depende apenas das interfaces (ports) PortabilityRepository e PrefixResolver — DIP aplicado.
 */
public class PortabilityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(PortabilityResolutionService.class);

    private final PortabilityRepository repository;
    private final PrefixResolver prefixResolver;
    private final PhoneNormalizer normalizer;
    /** RN1 → nome da operadora, carregado do Operadoras.csv */
    private final Map<String, String> operatorsDict;

    public PortabilityResolutionService(PortabilityRepository repository,
                                        PrefixResolver prefixResolver,
                                        PhoneNormalizer normalizer,
                                        Map<String, String> operatorsDict) {
        this.repository = repository;
        this.prefixResolver = prefixResolver;
        this.normalizer = normalizer;
        this.operatorsDict = operatorsDict;
    }

    /**
     * Processa uma lista de números brutos e retorna o mapeamento original → operadora.
     *
     * @param rawNumbers lista de números como vieram no arquivo de entrada
     * @return ProcessResult com operatorMap e sourceMap
     */
    public ProcessResult processNumbers(List<String> rawNumbers) {
        log.info("Processando {} números...", rawNumbers.size());

        // 1. Normalizar cada número
        Map<String, Long> originalToNormalized = new LinkedHashMap<>();
        for (String raw : rawNumbers) {
            Long normalized = normalizer.normalize(raw);
            originalToNormalized.put(raw, normalized); // null se inválido
        }

        // 2. Coletar números válidos para consulta em lote
        List<Long> validNumbers = originalToNormalized.values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 3. Consultar DuckDB → mapa telefone → rn1
        Map<Long, String> rn1Map = Collections.emptyMap();
        if (!validNumbers.isEmpty()) {
            try {
                rn1Map = repository.findOperatorsByPhoneNumbers(validNumbers);
                log.info("DuckDB retornou {} correspondências de portabilidade", rn1Map.size());
            } catch (Exception e) {
                log.error("Falha ao consultar DuckDB — continuando apenas com prefixo: {}", e.getMessage());
            }
        }

        // 4. Resolver rn1 → nome usando dicionário de operadoras
        Map<Long, String> phoneToName = new HashMap<>();
        for (Map.Entry<Long, String> entry : rn1Map.entrySet()) {
            String rn1 = entry.getValue();
            String nome = operatorsDict.getOrDefault(rn1, rn1); // usa rn1 como fallback se não mapeado
            phoneToName.put(entry.getKey(), nome);
        }

        // 5. Montar resultado por número original
        Map<String, String> operatorMap = new LinkedHashMap<>();
        Map<String, String> sourceMap = new LinkedHashMap<>();

        for (String raw : rawNumbers) {
            Long normalized = originalToNormalized.get(raw);

            if (normalized == null) {
                operatorMap.put(raw, "nao_identificado");
                sourceMap.put(raw, "invalido");
                continue;
            }

            if (phoneToName.containsKey(normalized)) {
                operatorMap.put(raw, phoneToName.get(normalized));
                sourceMap.put(raw, "portabilidade");
                continue;
            }

            // Fallback por prefixo (suporta 10 e 11 dígitos)
            String cleanDigits = normalizer.getCleanDigits(raw);
            String byPrefix = (cleanDigits != null) ? prefixResolver.resolve(cleanDigits) : null;
            if (byPrefix != null) {
                operatorMap.put(raw, byPrefix);
                sourceMap.put(raw, "prefixo");
                continue;
            }

            // Fallback extra: se for fixo (10 dígitos) e não achou, tenta a forma
            // "com 9" (11 dígitos) no prefixo, já que o mapa de prefixos foi gerado
            // a partir de celulares de 11 dígitos.
            if (cleanDigits != null && cleanDigits.length() == 10) {
                String asMobile = cleanDigits.substring(0, 2) + "9" + cleanDigits.substring(2);
                String byPrefixMobile = prefixResolver.resolve(asMobile);
                if (byPrefixMobile != null) {
                    operatorMap.put(raw, byPrefixMobile);
                sourceMap.put(raw, "prefixo");
                    continue;
                }
            }

            operatorMap.put(raw, "nao_identificado");
            sourceMap.put(raw, "nao_identificado");
        }

        long portCount = sourceMap.values().stream().filter("portabilidade"::equals).count();
        long prefixCount = sourceMap.values().stream().filter("prefixo"::equals).count();
        long notFound = sourceMap.values().stream().filter("nao_identificado"::equals).count();
        long invalid = sourceMap.values().stream().filter("invalido"::equals).count();

        log.info("Resultado: portabilidade={}, prefixo={}, nao_identificado={}, invalido={}",
                portCount, prefixCount, notFound, invalid);

        return new ProcessResult(operatorMap, sourceMap);
    }
}
