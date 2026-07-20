package com.portabilidade.infrastructure.repository;

import com.portabilidade.domain.exception.DataLoadingException;
import com.portabilidade.service.resolver.PortabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

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

        // Conexão read-only via DuckDB JDBC
        String url = "jdbc:duckdb:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url)) {
            // Configura acesso read-only
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA threads=4");
                // Garante índice para acelerar as consultas por telefone
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_port_telefone ON portabilidade(telefone)");
                } catch (SQLException idxEx) {
                    log.debug("Não foi possível criar índice (pode já existir ou ser read-only): {}",
                            idxEx.getMessage());
                }
            }

            List<List<Long>> batches = partition(numbers, BATCH_SIZE);
            log.info("Consultando DuckDB em {} lote(s) de até {} números", batches.size(), BATCH_SIZE);

            for (List<Long> batch : batches) {
                Map<Long, String> batchResult = queryBatch(conn, batch);
                result.putAll(batchResult);
            }

            // Fallback: números de 10 dígitos (fixos) que não foram achados como 11 dígitos.
            // A base também armazena fixos em 10 dígitos; tentamos a forma "sem o 9".
            List<Long> missingFixed = numbers.stream()
                    .filter(n -> n.toString().length() == 11 && n.toString().charAt(2) == '9')
                    .filter(n -> !result.containsKey(n))
                    .map(n -> Long.parseLong(n.toString().substring(0, 2) + n.toString().substring(3)))
                    .distinct()
                    .toList();
            if (!missingFixed.isEmpty()) {
                Map<Long, String> fixedResult = queryBatch(conn, missingFixed);
                // Mapeia de volta para a chave original (11 dígitos)
                for (Map.Entry<Long, String> e : fixedResult.entrySet()) {
                    String s = e.getKey().toString();
                    Long original = Long.parseLong(s.substring(0, 2) + "9" + s.substring(2));
                    result.putIfAbsent(original, e.getValue());
                }
            }

        } catch (SQLException e) {
            throw new DataLoadingException("Erro ao consultar DuckDB em: " + dbPath, e);
        }

        return result;
    }

    private Map<Long, String> queryBatch(Connection conn, List<Long> batch) throws SQLException {
        String placeholders = batch.stream()
                .map(n -> "?")
                .collect(Collectors.joining(","));

        String sql = "SELECT telefone, rn1 FROM portabilidade WHERE telefone IN (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < batch.size(); i++) {
                ps.setLong(i + 1, batch.get(i));
            }

            Map<Long, String> batchResult = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long telefone = rs.getLong("telefone");
                    String rn1 = rs.getString("rn1");
                    if (rn1 != null && !rn1.isBlank()) {
                        batchResult.put(telefone, rn1.trim());
                    }
                }
            }
            return batchResult;
        }
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
