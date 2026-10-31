package com.portabilidade.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppPaths")
class AppPathsTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("resolve todos os caminhos relativos ao baseDir")
    void resolvesAllPaths() {
        AppPaths p = new AppPaths(tmp.toString());
        assertEquals(tmp.toString(), p.getBaseDir());
        assertEquals(tmp.resolve("entrada").toString(), p.getInputDir());
        assertEquals(tmp.resolve("saida").toString(), p.getOutputDir());
        assertEquals(tmp.resolve("base_portabilidade").resolve("portabilidade.duckdb").toString(),
                p.getDuckDbPath());
        assertEquals(tmp.resolve("Operadoras.csv").toString(), p.getOperadoresCsv());
        assertEquals(tmp.resolve("prefixos_operadora.json").toString(), p.getPrefixosJson());
    }

    @Test
    @DisplayName("getOperadoresCsvCompleto retorna null quando nenhum candidato existe")
    void completoMissing() {
        AppPaths p = new AppPaths(tmp.toString());
        assertNull(p.getOperadoresCsvCompleto());
    }

    @Test
    @DisplayName("getOperadoresCsvCompleto prioriza Operadoras_completo.csv local")
    void completoFound() {
        try {
            java.nio.file.Files.createFile(tmp.resolve("Operadoras_completo.csv"));
        } catch (Exception e) {
            fail(e);
        }
        AppPaths p = new AppPaths(tmp.toString());
        assertEquals(tmp.resolve("Operadoras_completo.csv").toString(), p.getOperadoresCsvCompleto());
    }

    @Test
    @DisplayName("construtor padrão resolve BASE_DIR sem exceção")
    void defaultConstructor() {
        AppPaths p = new AppPaths();
        assertNotNull(p.getBaseDir());
        assertNotNull(p.getInputDir());
    }
}
