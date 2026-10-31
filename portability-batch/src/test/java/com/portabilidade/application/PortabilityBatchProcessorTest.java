package com.portabilidade.application;

import com.portabilidade.application.config.AppPaths;
import com.portabilidade.domain.model.PhoneNumber;
import com.portabilidade.infrastructure.file.CsvInputReader;
import com.portabilidade.infrastructure.file.FileScanner;
import com.portabilidade.infrastructure.file.InputFileReader;
import com.portabilidade.infrastructure.output.CsvOutputWriter;
import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PortabilityBatchProcessor (fim-a-fim com stubs)")
class PortabilityBatchProcessorTest {

    @TempDir
    Path tmp;

    private AppPaths paths;
    private final List<InputFileReader> readers = List.of(new CsvInputReader());

    /** Stub: DuckDB encontra apenas 11987654321 → rn1 55321 (CLARO). */
    private static final PortabilityRepository STUB_REPO = numbers -> {
        if (numbers.contains(11987654321L)) return Map.of(11987654321L, "55321");
        return Map.of();
    };
    /** Stub: prefixo 7 dígitos "2191234" → VIVO. */
    private static final PrefixResolver STUB_PREFIX = digits ->
            (digits != null && digits.startsWith("2191234")) ? "VIVO" : null;

    @BeforeEach
    void setUp() {
        paths = new AppPaths(tmp.toString());
    }

    private PortabilityBatchProcessor buildProcessor() {
        var dict = Map.of("55321", "CLARO");
        var service = new PortabilityResolutionService(
                STUB_REPO, STUB_PREFIX, new PhoneNormalizer(), dict);
        return new PortabilityBatchProcessor(
                paths, service, new FileScanner(), readers, new CsvOutputWriter());
    }

    @Test
    @DisplayName("processa CSV de entrada e gera saída enriquecida por posição")
    void endToEndCsv() throws Exception {
        Path inputDir = Path.of(paths.getInputDir());
        Files.createDirectories(inputDir);
        // mesmo telefone em duas formatações + um fixo via prefixo + um inválido
        Files.writeString(inputDir.resolve("clientes.csv"),
                "telefone;nome\n"
                        + "(11) 98765-4321;Ana\n"
                        + "11987654321;Bruno\n"
                        + "2112345678;Carla\n"
                        + "abc;Dani\n",
                StandardCharsets.UTF_8);

        buildProcessor().run();

        Path[] outputs;
        try (var s = Files.list(Path.of(paths.getOutputDir()))) {
            outputs = s.toList().toArray(new Path[0]);
        }
        assertEquals(1, outputs.length, "exatamente um arquivo FINAL_* gerado");

        String content = Files.readString(outputs[0], StandardCharsets.UTF_8);
        String[] lines = content.replace("\uFEFF", "").trim().split("\\R");
        assertTrue(lines[0].contains("operadora") && lines[0].contains("fonte_consulta"));

        // Ana: portabilidade CLARO | Bruno: portabilidade CLARO (duplicata não colide)
        // Carla: prefixo VIVO | Dani: invalido/nao_identificado
        assertTrue(lines[1].contains("CLARO") && lines[1].contains("portabilidade"), lines[1]);
        assertTrue(lines[2].contains("CLARO") && lines[2].contains("portabilidade"), lines[2]);
        assertTrue(lines[3].contains("VIVO") && lines[3].contains("prefixo"), lines[3]);
        assertTrue(lines[4].contains("nao_identificado") && lines[4].contains("invalido"), lines[4]);
    }

    @Test
    @DisplayName("run sem arquivos na entrada não gera saída nem exceção")
    void emptyInputIsNoop() {
        buildProcessor().run();
        assertFalse(Path.of(paths.getOutputDir()).toFile().exists()
                && listOutput().size() > 0);
    }

    @Test
    @DisplayName("arquivo sem coluna 'telefone' é ignorado com aviso")
    void missingPhoneColumn() throws Exception {
        Path inputDir = Path.of(paths.getInputDir());
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("sem_telefone.csv"), "nome;cidade\nAna;SP\n");

        buildProcessor().run();
        assertEquals(0, listOutput().size());
    }

    @Test
    @DisplayName("erro em um arquivo não impede o processamento dos demais")
    void errorIsolation() throws Exception {
        Path inputDir = Path.of(paths.getInputDir());
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("bom.csv"), "telefone\n11987654321\n");
        // .xlsx inválido (conteúdo lixo) → leitor de Excel falha, mas bom.csv deve ser processado
        Files.writeString(inputDir.resolve("lixo.xlsx"), "not an excel file");

        buildProcessor().run();
        assertEquals(1, listOutput().size(), "somente o arquivo válido gera saída");
    }

    @Test
    @DisplayName("PhoneNumber stub sanity: normalizador usado pelo serviço aceita formato com máscara")
    void normalizerIntegration() {
        var n = new PhoneNormalizer();
        PhoneNumber p = new PhoneNumber("(11) 98765-4321", n.normalize("(11) 98765-4321"));
        assertTrue(p.isValid());
        assertEquals(11987654321L, p.normalized());
    }

    private List<Path> listOutput() {
        Path outDir = Path.of(paths.getOutputDir());
        if (!Files.exists(outDir)) return List.of();
        try (var s = Files.list(outDir)) {
            return s.toList();
        } catch (Exception e) {
            fail(e);
            return List.of();
        }
    }
}
