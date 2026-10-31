package com.portabilidade.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Resolve todos os caminhos usados pela aplicação a partir do BASE_DIR.
 *
 * BASE_DIR é determinado como o diretório onde o JAR está sendo executado
 * (ou o diretório de trabalho atual em ambiente de desenvolvimento).
 *
 * Estrutura esperada em BASE_DIR:
 *   entrada/                        ← arquivos de entrada .csv/.xlsx
 *   saida/                          ← arquivos gerados
 *   base_portabilidade/portabilidade.duckdb
 *   Operadoras.csv
 *   prefixos_operadora.json
 */
public class AppPaths {

    private static final Logger log = LoggerFactory.getLogger(AppPaths.class);

    private final String baseDir;

    public AppPaths() {
        this.baseDir = resolveBaseDir();
        log.info("BASE_DIR resolvido: {}", baseDir);
    }

    /** Construtor para testes ou execuções com base em diretório explícito. */
    public AppPaths(String baseDir) {
        this.baseDir = baseDir;
    }

    private String resolveBaseDir() {
        try {
            // Tenta usar o diretório do JAR em execução
            File jarFile = new File(
                    AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            File jarDir = jarFile.isFile() ? jarFile.getParentFile() : jarFile;
            return jarDir.getAbsolutePath();
        } catch (URISyntaxException | SecurityException e) {
            // Fallback para diretório de trabalho atual
            log.debug("Usando working directory como BASE_DIR: {}", e.getMessage());
            return System.getProperty("user.dir");
        }
    }

    public String getBaseDir()         { return baseDir; }
    public String getInputDir()        { return resolve("entrada"); }
    public String getOutputDir()       { return resolve("saida"); }
    public String getDuckDbPath()      { return resolve("base_portabilidade", "portabilidade.duckdb"); }
    public String getOperadoresCsv()   { return resolve("Operadoras.csv"); }
    public String getPrefixosJson()    { return resolve("prefixos_operadora.json"); }

    /**
     * Caminho para o dicionário de operadoras mais completo.
     * O arquivo é opcional e deve estar dentro de BASE_DIR; não há caminho
     * absoluto específico de máquina (isso quebrava containers/onboarding).
     */
    public String getOperadoresCsvCompleto() {
        String completo = resolve("Operadoras_completo.csv");
        return new File(completo).exists() ? completo : null;
    }

    private String resolve(String... parts) {
        Path path = Path.of(baseDir);
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path.toString();
    }
}
