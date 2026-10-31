package com.portabilidade;

import com.portabilidade.application.PortabilityBatchProcessor;
import com.portabilidade.application.config.AppPaths;
import com.portabilidade.infrastructure.file.*;
import com.portabilidade.infrastructure.loader.CsvOperatorLoader;
import com.portabilidade.infrastructure.loader.JsonPrefixMapLoader;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.infrastructure.repository.DuckDbPortabilityRepository;
import com.portabilidade.infrastructure.resolver.JsonPrefixResolver;
import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Ponto de entrada da aplicação.
 *
 * Realiza a injeção de dependências manual (sem framework),
 * instanciando todos os componentes na ordem correta e
 * delegando a execução ao PortabilityBatchProcessor.
 */
public class PortabilityApplication {

    private static final Logger log = LoggerFactory.getLogger(PortabilityApplication.class);

    public static void main(String[] args) {
        log.info("Portabilidade Numérica Batch — iniciando");

        try {
            // ── Configuração de caminhos ──────────────────────────────────────
            AppPaths paths = new AppPaths();

            log.info("Caminhos configurados:");
            log.info("  entrada/         : {}", paths.getInputDir());
            log.info("  saida/           : {}", paths.getOutputDir());
            log.info("  DuckDB           : {}", paths.getDuckDbPath());
            log.info("  Operadoras.csv   : {}", paths.getOperadoresCsv());
            log.info("  prefixos JSON    : {}", paths.getPrefixosJson());

            // ── Loaders — carregam dados auxiliares ───────────────────────────
            CsvOperatorLoader operatorLoader = new CsvOperatorLoader();
            Map<String, String> operatorsDict = operatorLoader.load(paths.getOperadoresCsv());
            log.info("Dicionário de operadoras (exemplo): {} entradas", operatorsDict.size());

            // Tenta carregar um dicionário COMPLETO de operadoras (cobertura total de RN1).
            String csvCompleto = paths.getOperadoresCsvCompleto();
            if (csvCompleto != null) {
                Map<String, String> completo = operatorLoader.load(csvCompleto);
                if (!completo.isEmpty()) {
                    log.info("Dicionário de operadoras COMPLETO carregado de '{}': {} entradas",
                            csvCompleto, completo.size());
                    operatorsDict = completo;
                }
            } else {
                log.warn("Nenhum dicionário completo de operadoras encontrado — usando o de exemplo (baixa cobertura).");
            }

            JsonPrefixMapLoader prefixLoader = new JsonPrefixMapLoader();
            Map<String, String> prefixMap = prefixLoader.load(paths.getPrefixosJson());
            log.info("Mapa de prefixos: {} entradas", prefixMap.size());

            // ── Infraestrutura — repositório e resolvers ──────────────────────
            DuckDbPortabilityRepository repository =
                    new DuckDbPortabilityRepository(paths.getDuckDbPath());

            JsonPrefixResolver prefixResolver = new JsonPrefixResolver(prefixMap);

            // ── Serviços ──────────────────────────────────────────────────────
            PhoneNormalizer normalizer = new PhoneNormalizer();
            PortabilityResolutionService resolutionService = new PortabilityResolutionService(
                    repository,
                    prefixResolver,
                    normalizer,
                    operatorsDict
            );

            // ── Leitores de arquivo ───────────────────────────────────────────
            List<InputFileReader> readers = List.of(
                    new CsvInputReader(),
                    new ExcelInputReader()
            );

            // ── Output ────────────────────────────────────────────────────────
            CsvOutputWriter outputWriter = new CsvOutputWriter();

            // ── Orquestrador batch ────────────────────────────────────────────
            FileScanner fileScanner = new FileScanner();
            PortabilityBatchProcessor processor = new PortabilityBatchProcessor(
                    paths,
                    resolutionService,
                    fileScanner,
                    readers,
                    outputWriter
            );

            processor.run();
            log.info("Portabilidade Numérica Batch — concluído com sucesso");

        } catch (Exception e) {
            log.error("Erro fatal na aplicação: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
