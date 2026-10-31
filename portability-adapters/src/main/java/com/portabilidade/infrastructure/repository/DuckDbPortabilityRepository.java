package com.portabilidade.infrastructure.repository;

import com.portabilidade.domain.exception.DataLoadingException;
import com.portabilidade.service.resolver.PortabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;


/**
 * Implementação de PortabilityRepository usando DuckDB JDBC (read-only).
 *
 * Consultas são feitas em lotes de até BATCH_SIZE números para evitar
 * problemas com IN clauses muito grandes.
 *
 * A tabela esperada no DuckDB tem pelo menos as colunas:
 *   telefone BIGINT, rn1 VARCHAR
 */
public class DuckDbPortabilityRepository implements PortabilityRepository {

    private static final Logger log = LoggerFactory.getLogger(DuckDbPortabilityRepository.class);
    private static final int BATCH_SIZE = 5000;

    private final String dbPath;

    public DuckDbPortabilityRepository(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers) {
        if (numbers == null || numbers.isEmpty()) return Collections.emptyMap();

        Map<Long, String> result = new HashMap<>();

        // Conexão via DuckDB JDBC. Em produção a base é aberta read-only (evita
        // lock/corrupção com o job de ingest). O CREATE INDEX abaixo é idempotente
        // e só tem efeito em bases graváveis (ex.: fixtures de teste); em modo
        // read-only ele falha silenciosamente e o índice criado no ingest é usado.
        String url = "jdbc:duckdb:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA threads=4");
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_port_telefone ON portabilidade(telefone)");
                } catch (SQLException idxEx) {
                    log.debug("Não foi possível criar índice (pode já existir ou ser read-only): {}",
                            idxEx.getMessage());
                }
            } catch (SQLException pragmaEx) {
                log.debug("PRAGMA não aplicável nesta base: {}", pragmaEx.getMessage());
            }

            List<List<Long>> batches = partition(numbers, BATCH_SIZE);
            log.info("Consultando DuckDB em {} lote(s) de até {} números", batches.size(), BATCH_SIZE);

            for (List<Long> batch : batches) {
                Map<Long, String> batchResult = queryBatch(conn, batch);
                result.putAll(batchResult);
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
                Map<Long, String> fixedResult = queryBatch(conn, List.copyOf(fixedToOriginal.keySet()));
                // Mapeia de volta para a chave original (11 dígitos)
                for (Map.Entry<Long, String> e : fixedResult.entrySet()) {
                    Long original = fixedToOriginal.get(e.getKey());
                    if (original != null) {
                        result.putIfAbsent(original, e.getValue());
                    }
                }
            }

        } catch (SQLException e) {
            throw new DataLoadingException("Erro ao consultar DuckDB em: " + dbPath, e);
        }

        return result;
    }

    /**
     * Consulta um lote via staging table + JOIN.
     *
     * Evita o uso de prepared statements com IN (?, ?, ...) e bind por índice,
     * que apresentam comportamento instável no driver DuckDB JDBC (parâmetros
     * podiam ser ignorados/zerados, retornando resultados errados). A abordagem
     * de staging table é determinística e continua eficiente para lotes grandes
     * (o planner do DuckDB faz hash-join contra a tabela de consulta).
     */
    private Map<Long, String> queryBatch(Connection conn, List<Long> batch) throws SQLException {
        Map<Long, String> batchResult = new HashMap<>();
        if (batch.isEmpty()) return batchResult;

        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TEMPORARY TABLE IF NOT EXISTS tmp_query_batch (telefone BIGINT)");
        }
        try {
            // Limpa o lote anterior e popula o atual em uma única transação de statements.
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM tmp_query_batch");
            }

            String insertSql = "INSERT INTO tmp_query_batch (telefone) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (long phone : batch) {
                    ps.setLong(1, phone);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            String joinSql = """
                    SELECT q.telefone AS telefone, p.rn1 AS rn1
                    FROM tmp_query_batch q
                    JOIN portabilidade p ON p.telefone = q.telefone
                    """;
            try (Statement st = conn.createStatement();
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
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM tmp_query_batch");
            } catch (SQLException cleanupEx) {
                log.debug("Falha ao limpar tmp_query_batch: {}", cleanupEx.getMessage());
            }
        }

        return batchResult;
    }

    /**
     * Particiona uma lista em sublistas de tamanho máximo.
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
