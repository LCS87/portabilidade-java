package com.portabilidade.infrastructure.repository;



import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Provisiona a base DuckDB de portabilidade (Fase 1/2 do PLANO_MELHORIAS).
 *
 * Uso: java -cp ... DuckDbDatabaseProvisioner &lt;base_dir&gt; &lt;arquivo_portabilidade.csv&gt; [out.duckdb]
 *
 * O arquivo de origem deve ter colunas telefone (BIGINT) e rn1 (VARCHAR),
 * separadas por ; ou , (detectado automaticamente). A carga é feita via
 * COPY + JOIN contra um CSV gerado com tipos normalizados, e a tabela final
 * já sai com índice em telefone (query path read-only nunca escreve).
 */
public final class DuckDbDatabaseProvisioner {

    private DuckDbDatabaseProvisioner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: DuckDbDatabaseProvisioner <baseDir> <portabilidade.csv> [saida.duckdb]");
            System.exit(2);
        }
        Path baseDir = Path.of(args[0]);
        Path source = Path.of(args[1]);
        Path out = args.length > 2 ? baseDir.resolve(args[2])
                : baseDir.resolve("portabilidade.duckdb");
        Files.createDirectories(out.getParent());
        long t0 = System.currentTimeMillis();
        long rows = provision(source, out);
        System.out.printf("Base provisionada: %s (%d linhas, %d ms)%n",
                out, rows, System.currentTimeMillis() - t0);
    }

    /** Cria (sobrescrevendo) a base portabilidade(telefone BIGINT, rn1 VARCHAR) + índice. */
    public static long provision(Path sourceCsv, Path duckdbFile) throws Exception {
        String sep = detectSeparator(sourceCsv);
        Path tmp = Files.createTempFile("prov_portabilidade_", ".csv");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            normalizeToCsv(sourceCsv, sep, w);
        }
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:" + duckdbFile);
             Statement st = c.createStatement()) {
            // duckdb_jdbc 1.1.x: o separador é passado por nome ('sep'), nao 'delimiter'.
            st.execute("CREATE TABLE portabilidade AS "
                    + "SELECT CAST(telefone AS BIGINT) AS telefone, rn1 "
                    + "FROM read_csv('" + tmp.toAbsolutePath().toString().replace("'", "''")
                    + "', sep=',', header=true)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_port_telefone ON portabilidade(telefone)");
            var rs = st.executeQuery("SELECT count(*) FROM portabilidade");
            rs.next();
            long rows = rs.getLong(1);
            c.close();
            return rows;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String detectSeparator(Path csv) throws Exception {
        try (var lines = Files.lines(csv, StandardCharsets.UTF_8)) {
            String first = lines.findFirst().orElse(",");
            int semi = first.indexOf(';'), comma = first.indexOf(',');
            return (semi >= 0 && semi < comma) || comma < 0 ? ";" : ",";
        }
    }

    /** Copia telefone/rn1 (colunas 0 e 1 após header) escapando aspas duplas. */
    private static void normalizeToCsv(Path sourceCsv, String sep, BufferedWriter w) throws Exception {
        w.write("telefone,rn1\n");
        try (var r = Files.newBufferedReader(sourceCsv, StandardCharsets.UTF_8)) {
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.isBlank()) continue;
                int idx = line.indexOf(sep);
                if (idx <= 0) continue;
                String tel = line.substring(0, idx).trim();
                String rn1 = line.substring(idx + sep.length()).trim();
                if (!tel.matches("\\d+")) continue;
                w.write(tel);
                w.write(',');
                w.write('"');
                w.write(rn1.replace("\"", "\"\""));
                w.write('"');
                w.write('\n');
            }
        }
    }
}
