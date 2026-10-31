package com.portabilidade.infrastructure;

import com.portabilidade.infrastructure.file.CsvInputReader;
import com.portabilidade.infrastructure.file.FileScanner;
import com.portabilidade.infrastructure.loader.CsvOperatorLoader;
import com.portabilidade.infrastructure.loader.JsonPrefixMapLoader;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.infrastructure.repository.DuckDbPortabilityRepository;
import com.portabilidade.infrastructure.resolver.JsonPrefixResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de integração dos adapters (DuckDB, CSV, JSON)")
class AdaptersIntegrationTest {

    @TempDir
    Path tmp;

    // ---------- DuckDB ----------

    @Test
    @DisplayName("DuckDB: encontra rn1 por telefone e faz fallback 11d→10d")
    void duckDbLookupAndFixedFallback() throws Exception {
        Path db = tmp.resolve("portabilidade.duckdb");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + db)) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE portabilidade (telefone BIGINT, rn1 VARCHAR)");
                // IMPORTANTE: DuckDB trata "--" como início de comentário até o fim da LINHA.
                // Os comentários precisam ir em linhas separadas; no meio de um INSERT com
                // múltiplos VALUES eles "engolem" a linha seguinte e corrompem os dados.
                st.execute("""
                        INSERT INTO portabilidade VALUES
                        (1133334444, '55321'),    -- fixo 10d: fallback do 11d 11933334444
                        (21912345678, '12345'),   -- celular direto
                        (31999999999, '')         -- rn1 vazio → deve ser ignorado
                        """);
            }

        }

        var repo = new DuckDbPortabilityRepository(db.toString());
        Map<Long, String> result = repo.findOperatorsByPhoneNumbers(
                List.of(11933334444L, 21912345678L, 31999999999L, 4188888888L));

        assertEquals("55321", result.get(11933334444L), "fallback 11d→10d deve mapear de volta");
        assertEquals("12345", result.get(21912345678L));
        assertFalse(result.containsKey(31999999999L), "rn1 em branco deve ser descartado");
        assertFalse(result.containsKey(4188888888L));
    }

    @Test
    @DisplayName("DuckDB: entrada null/vazia retorna mapa vazio sem abrir conexão")
    void duckDbEmptyInput() {
        var repo = new DuckDbPortabilityRepository("/caminho/inexistente.db");
        assertTrue(repo.findOperatorsByPhoneNumbers(null).isEmpty());
        assertTrue(repo.findOperatorsByPhoneNumbers(List.of()).isEmpty());
    }

    // ---------- CsvInputReader ----------

    @Test
    @DisplayName("CsvInputReader: detecta separador ';', mapeia alias 'CELULAR' → telefone")
    void csvReaderSeparatorAndAlias() throws Exception {
        Path file = tmp.resolve("entrada.csv");
        Files.writeString(file, "CELULAR;NOME\n(11) 98765-4321;João\n21912345678;Maria\n",
                StandardCharsets.UTF_8);

        List<Map<String, String>> rows = new CsvInputReader().read(file.toString());
        assertEquals(2, rows.size());
        assertEquals("(11) 98765-4321", rows.get(0).get("telefone"));
        assertEquals("João", rows.get(0).get("nome"));
        assertEquals("Maria", rows.get(1).get("nome"));
    }

    @Test
    @DisplayName("CsvInputReader: arquivo inexistente → DataLoadingException")
    void csvReaderMissingFile() {
        var reader = new CsvInputReader();
        assertThrows(com.portabilidade.domain.exception.DataLoadingException.class,
                () -> reader.read(tmp.resolve("nao-existe.csv").toString()));
    }

    @Test
    @DisplayName("CsvInputReader: supports só aceita .csv")
    void csvReaderSupports() {
        var reader = new CsvInputReader();
        assertTrue(reader.supports("a.csv"));
        assertTrue(reader.supports("A.CSV"));
        assertFalse(reader.supports("a.xlsx"));
        assertFalse(reader.supports(null));
    }

    // ---------- FileScanner ----------

    @Test
    @DisplayName("FileScanner: lista apenas .csv/.xlsx; diretório inexistente → vazio")
    void fileScanner() throws Exception {
        Files.writeString(tmp.resolve("a.csv"), "telefone\n");
        Files.writeString(tmp.resolve("b.xlsx"), "x");
        Files.writeString(tmp.resolve("ignore.txt"), "x");
        Files.createDirectory(tmp.resolve("sub.csv")); // diretório não deve entrar

        List<String> found = new FileScanner().scan(tmp.toString());
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(s -> s.endsWith("a.csv")));
        assertTrue(found.stream().anyMatch(s -> s.endsWith("b.xlsx")));

        assertTrue(new FileScanner().scan(tmp.resolve("nope").toString()).isEmpty());
    }

    // ---------- CsvOutputWriter ----------

    @Test
    @DisplayName("CsvOutputWriter: gera FINAL_*.csv com colunas enriquecidas; vazio → null")
    void csvOutputWriter() throws Exception {
        Path outDir = tmp.resolve("saida");
        var writer = new CsvOutputWriter();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("telefone", "11987654321");
        row.put("operadora", "CLARO");
        row.put("fonte_consulta", "portabilidade");

        String generated = writer.write(List.of(row), "exemplo.csv", outDir.toString());
        assertNotNull(generated);
        Path output = Path.of(generated);
        assertTrue(output.getFileName().toString().startsWith("FINAL_"));
        assertTrue(output.getFileName().toString().endsWith("_exemplo.csv"));

        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("telefone"));
        assertTrue(content.replace(';', ',').contains("CLARO"));
        assertNull(writer.write(List.of(), "vazio", outDir.toString()));
    }

    // ---------- Loaders / Resolver ----------

    @Test
    @DisplayName("JsonPrefixMapLoader + JsonPrefixResolver: resolve prefixo 7d e fallback fixo")
    void jsonPrefixEndToEnd() throws Exception {
        Path json = tmp.resolve("prefixos.json");
        Files.writeString(json, """
                {
                  "1198765": {"operadora": "claro", "rn1": "55321", "qtd": 10},
                  "2191234": {"operadora": "VIVO", "rn1": "12345", "qtd": 5}
                }""");

        Map<String, String> map = new JsonPrefixMapLoader().load(json.toString());
        assertEquals(2, map.size());
        assertEquals("CLARO", map.get("1198765"), "normalizado para maiúsculas");

        var resolver = new JsonPrefixResolver(map);
        assertEquals("CLARO", resolver.resolve("11987654321"));
        // fixo 10d → candidato 11d "2191234..." deve casar
        assertEquals("VIVO", resolver.resolve("2112345678"));
        assertNull(resolver.resolve("41123456789"));
        assertNull(resolver.resolve("12345")); // < 7 dígitos
        assertNull(resolver.resolve(null));

        // loader tolera arquivo inexistente
        assertTrue(new JsonPrefixMapLoader().load(tmp.resolve("nope.json").toString()).isEmpty());
    }

    @Test
    @DisplayName("CsvOperatorLoader: carrega mapa rn1 → nome do CSV")
    void csvOperatorLoader() throws Exception {
        Path csv = tmp.resolve("operadoras.csv");
        Files.writeString(csv, "rn1,nome\n55321,CLARO\n12345,VIVO\n", StandardCharsets.UTF_8);

        Map<String, String> dict = new CsvOperatorLoader().load(csv.toString());
        assertEquals("CLARO", dict.get("55321"));
        assertEquals("VIVO", dict.get("12345"));
    }
}
