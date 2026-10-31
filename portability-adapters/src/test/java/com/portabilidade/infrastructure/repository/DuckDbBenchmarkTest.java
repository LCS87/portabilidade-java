package com.portabilidade.infrastructure.repository;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark da Fase 1 (PLANO_MELHORIAS): staging table + COPY vs abordagem
 * antiga IN (?×N)/addBatch. Executa como teste de integração — além de medir,
 * valida a corretude do JOIN (todos os números presentes na base são resolvidos).
 */
@DisplayName("DuckDB benchmark: staging+COPY (Fase 1)")
class DuckDbBenchmarkTest {

    private static final int BASE_ROWS = 200_000;
    private static final int QUERY_NUMBERS = 100_000;

    @TempDir
    Path tmp;
    private String dbPath;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tmp.resolve("portability.duckdb").toString();
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE portabilidade (telefone BIGINT, rn1 VARCHAR)");
            // geração da base via SQL do próprio DuckDB (rápido, sem round-trip JDBC)
            // SQL literal: evita conflito entre '%' de módulo e formatador String.formatted
            st.execute("INSERT INTO portabilidade "
                    + "SELECT 11000000000 + (range % 200000000)::BIGINT AS telefone, "
                    + "lpad(((range % 99999) + 1)::VARCHAR, 5, '0') AS rn1 "
                    + "FROM range(" + BASE_ROWS + ")");
        }
    }

    @Test
    @DisplayName("consulta 100k números em base de 200k via staging+COPY")
    void benchmarkStagingCopy() {
        List<Long> numbers = new ArrayList<>(QUERY_NUMBERS);
        for (int i = 0; i < QUERY_NUMBERS; i++) {
            numbers.add(11000000000L + i * 2); // pares → todos existem na base
        }

        long start = System.nanoTime();
        Map<Long, String> result;
        try (DuckDbPortabilityRepository repo = new DuckDbPortabilityRepository(dbPath)) {
            result = repo.findOperatorsByPhoneNumbers(numbers);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(QUERY_NUMBERS, result.size(), "todos os números devem ser resolvidos");
        System.out.printf("[BENCHMARK] staging+COPY: %,d numeros -> %,d resultados em %d ms%n",
                QUERY_NUMBERS, result.size(), elapsedMs);
        // gate generoso para CI (discos lentos): deve resolver 100k em < 30s
        assertTrue(elapsedMs < 30_000, "query de 100k deveria completar em <30s, levou " + elapsedMs + "ms");
    }
}
