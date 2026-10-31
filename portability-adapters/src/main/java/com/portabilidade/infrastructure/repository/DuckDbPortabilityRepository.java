package com.portabilidade.infrastructure.repository;

import com.portabilidade.domain.exception.DataLoadingException;
import com.portabilidade.service.resolver.PortabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Implementação de PortabilityRepository usando DuckDB JDBC.
 *
 * Otimizações da Fase 1 (PLANO_MELHORIAS):
 *  - Conexão única reutilizada por execução (lazy, mantida entre chamadas e
 *    liberada em {@link #close()}) — não abre/fecha o banco a cada lote.
 *  - Consulta via staging table + JOIN (sem IN (?×N)) — determinística e
 *    eficiente para lotes grandes (hash-join no planner do DuckDB).
 *  - Carga do lote via COPY de CSV temporário: benchmark com 100k números
 *    mostrou 163 ms (COPY) vs 21.324 ms (addBatch do driver) — ~130x mais
 *    rápido; o addBatch do DuckDB JDBC é patologicamente lento.
 *  - CREATE INDEX apenas em bases graváveis (step de provisionamento/ingest);
 *    em produção a base read-only já vem indexada e a query path não escreve.
 *  - Thread-safety: métodos sincronizados (DuckDB não permite transações
 *    simultâneas na mesma Connection). Para paralelismo real use uma instância
 *    por tarefa — bases read-only aceitam múltiplas conexões/leituras.
 *
 * A tabela esperada no DuckDB tem pelo menos as colunas:
 *   telefone BIGINT, rn1 VARCHAR
 */
public class DuckDbPortabilityRepository implements PortabilityRepository {

    private static final Logger log = LoggerFactory.getLogger(DuckDbPortabilityRepository.class);
    private static final int BATCH_SIZE = 50_000;

    private final String dbPath;
    private final boolean writable; // true apenas em fixtures/provisionamento
    private Connection conn;

    public DuckDbPortabilityRepository(String dbPath) {
        this(dbPath, false);
    }

    /** @param writable abre a base para escrita (cria índice idempotente). Producao usa false (read-only). */
    public DuckDbPortabilityRepository(String dbPath, boolean writable) {
        this.dbPath = dbPath;
        this.writable = writable;
    }

    /** Conexão única por instância, criada lazy e reaproveitada entre chamadas. */
    private synchronized Connection connection() {
        if (conn == null) {
            try {
                conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath);
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA threads=4");
                    if (writable) {
                        ensureIndex(st);
                    }
                }
            } catch (SQLException e) {
                throw new DataLoadingException("Erro ao abrir DuckDB em: " + dbPath, e);
            }
        }
        return conn;
    }

    /**
     * Índice idempotente executado no provisionamento da base (ou fixtures de
     * teste). Em produção a base é lida read-only e o índice já existe — a
     * query path nunca tenta escrevê-lo.
     */
    private void ensureIndex(Statement stmt) {
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_port_telefone ON portabilidade(telefone)");
        } catch (SQLException idxEx) {
            log.debug("Não foi possível criar índice (índice existente ou limitação da base): {}",
                    idxEx.getMessage());
        }
    }

    @Override
    public synchronized Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers) {
        if (numbers == null || numbers.isEmpty()) return Collections.emptyMap();

        Map<Long, String> result = new HashMap<>();
        Connection c = connection();

        try {
            List<List<Long>> batches = partition(numbers, BATCH_SIZE);
            log.debug("Consultando DuckDB em {} lote(s) de até {} números", batches.size(), BATCH_SIZE);

            for (List<Long> batch : batches) {
                result.putAll(queryBatch(c, batch));
            }

            // Fallback: celulares de 11 dígitos (DDD + 9 + 8 dígitos) que não foram
            // achados diretamente. A base pode armazenar o número na forma antiga de
            // 10 dígitos (sem o nono dígito); tentamos a forma "sem o 9" e mapeamos
            // o resultado de volta para a chave original de 11 dígitos.
            Map<Long, Long> fixedToOriginal = new HashMap<>();
            for (Long n : numbers) {
                if (result.containsKey(n)) continue;
                String s = n.toString();
                if (s.length() != 11 || s.charAt(2) != '9') continue; // só celulares válidos
                long fixed10 = Long.parseLong(s.substring(0, 2) + s.substring(3));
                fixedToOriginal.putIfAbsent(fixed10, n);
            }
            if (!fixedToOriginal.isEmpty()) {
                Map<Long, String> fixedResult = queryBatch(c, List.copyOf(fixedToOriginal.keySet()));
                for (Map.Entry<Long, String> e : fixedResult.entrySet()) {
                    Long original = fixedToOriginal.get(e.getKey());
                    if (original != null) {
                        result.putIfAbsent(original, e.getValue());
                    }
                }
            }

        } catch (SQLException | IOException e) {
            throw new DataLoadingException("Erro ao consultar DuckDB em: " + dbPath, e);
        }

        return result;
    }

    /**
     * Consulta um lote via staging table + JOIN, carregando os números com COPY
     * de um CSV temporário (benchmark Fase 1: ~130x mais rápido que addBatch).
     */
    private Map<Long, String> queryBatch(Connection c, List<Long> batch) throws SQLException, IOException {
        Map<Long, String> batchResult = new HashMap<>();
        if (batch.isEmpty()) return batchResult;

        Path csvFile = Files.createTempFile("portability_query_", ".csv");
        try {
            try (BufferedWriter w = Files.newBufferedWriter(csvFile, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder(16);
                for (long phone : batch) {
                    sb.setLength(0);
                    w.write(sb.append(phone).append('\n').toString());
                }
            }

            try (Statement st = c.createStatement()) {
                st.execute("DROP TABLE IF EXISTS tmp_query_batch");
                st.execute("CREATE TEMPORARY TABLE tmp_query_batch (telefone BIGINT)");
                st.execute("COPY tmp_query_batch FROM '" + csvFile.toAbsolutePath()
                        + "' (FORMAT csv, HEADER false)");
            }

            String joinSql = """
                    SELECT q.telefone AS telefone, p.rn1 AS rn1
                    FROM tmp_query_batch q
                    JOIN portabilidade p ON p.telefone = q.telefone
                    """;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(joinSql)) {
                while (rs.next()) {
                    long telefone = rs.getLong("telefone");
                    String rn1 = rs.getString("rn1");
                    if (rn1 != null && !rn1.isBlank()) {
                        batchResult.putIfAbsent(telefone, rn1.trim());
                    }
                }
            }
        } finally {
            try (Statement st = c.createStatement()) {
                st.execute("DROP TABLE IF EXISTS tmp_query_batch");
            } catch (SQLException cleanupEx) {
                log.debug("Falha ao remover tmp_query_batch: {}", cleanupEx.getMessage());
            }
            Files.deleteIfExists(csvFile);
        }

        return batchResult;
    }

    @Override
    public synchronized void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.debug("Falha ao fechar conexão DuckDB: {}", e.getMessage());
            }
            conn = null;
        }
    }

    /** Particiona uma lista em sublistas de tamanho máximo. */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
