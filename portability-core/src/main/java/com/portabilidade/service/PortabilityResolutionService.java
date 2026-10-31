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
     * @return ProcessResult com listas paralelas por posição (raw, operador, fonte)
     */
    public ProcessResult processNumbers(List<String> rawNumbers) {
        log.info("Processando {} números...", rawNumbers.size());

        // 1. Normalizar POR POSIÇÃO (mapa por texto bruto colidia quando o mesmo
        //    telefone aparecia formatado de formas diferentes ou duplicado)
        List<Long> normalizedPerLine = new ArrayList<>(rawNumbers.size());
        for (String raw : rawNumbers) {
            normalizedPerLine.add(normalizeOne(raw)); // null se inválido
        }

        // 2. Coletar números válidos para consulta em lote
        List<Long> validNumbers = normalizedPerLine.stream()
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

        // 5. Montar resultado POR POSIÇÃO (indexado por linha, não pelo texto bruto —
        //    elimina colisões quando o mesmo telefone aparece formatado de formas diferentes)
        List<String> operators = new ArrayList<>(rawNumbers.size());
        List<String> sources   = new ArrayList<>(rawNumbers.size());

        for (int idx = 0; idx < rawNumbers.size(); idx++) {
            String raw = rawNumbers.get(idx);
            Long normalized = normalizedPerLine.get(idx);

            if (normalized == null) {
                operators.add(ProcessResult.NOT_IDENTIFIED);
                sources.add("invalido");
                continue;
            }

            String byPortability = phoneToName.get(normalized);
            if (byPortability != null) {
                operators.add(byPortability);
                sources.add("portabilidade");
                continue;
            }

            // Fallback por prefixo (JsonPrefixResolver já tenta 7/6/5 dígitos nas
            // formas 10d e 11d internamente)
            String cleanDigits = normalizer.getCleanDigits(raw);
            String byPrefix = (cleanDigits != null) ? prefixResolver.resolve(cleanDigits) : null;
            if (byPrefix != null) {
                operators.add(byPrefix);
                sources.add("prefixo");
                continue;
            }

            operators.add(ProcessResult.NOT_IDENTIFIED);
            sources.add(ProcessResult.NOT_IDENTIFIED);
        }

        long portCount = sources.stream().filter("portabilidade"::equals).count();
        long prefixCount = sources.stream().filter("prefixo"::equals).count();
        long notFound = sources.stream().filter(ProcessResult.NOT_IDENTIFIED::equals).count();
        long invalid = sources.stream().filter("invalido"::equals).count();

        log.info("Resultado: portabilidade={}, prefixo={}, nao_identificado={}, invalido={}",
                portCount, prefixCount, notFound, invalid);

        return new ProcessResult(rawNumbers, operators, sources);
    }

    /** Normalização tolerante a null para uma única linha. */
    private Long normalizeOne(String raw) {
        return raw == null ? null : normalizer.normalize(raw);
    }
}
