package com.portabilidade.infrastructure.batch;

import com.portabilidade.infrastructure.file.CsvInputReader;
import com.portabilidade.infrastructure.file.InputFileReader;
import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.resolver.PortabilityRepository;
import com.portabilidade.service.resolver.PrefixResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 4 (PLANO_MELHORIAS): contrato do pipeline compartilhado entre o
 * executor em memória (virtual threads) e o listener RabbitMQ.
 * - happy path: chunks → resolução → CSV streaming + progresso/totais
 * - falhas isoladas: coluna 'telefone' ausente, limite maxRows, formato inválido
 */
class BatchExecutionServiceTest {

    /** Repo fake: 11999998888 tem portabilidade para VIVO; demais, none. */
    private static final class FakeRepo implements PortabilityRepository {
        @Override
        public Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers) {
            return numbers.contains(11999998888L) ? Map.of(11999998888L, "VIVO") : Map.of();
        }
    }

    /** Prefixos mínimos: 11 = operadora base (mesma semântica do core). */
    private static final class FakePrefixResolver implements PrefixResolver {
        @Override
        public String resolve(String normalizedNumber) {
            return normalizedNumber.startsWith("11") ? "CLARO" : null;
        }
    }

    /** ObjectProvider vazio: simula contexto sem actuator (meterRegistry ausente). */
    @SuppressWarnings("unchecked")
    private static final ObjectProvider<io.micrometer.core.instrument.MeterRegistry> NO_METRICS =
            Mockito.mock(ObjectProvider.class);

    private static BatchExecutionService service(Path outDir, int chunkSize, long maxRows) {
        PortabilityResolutionService resolution = new PortabilityResolutionService(
                new FakeRepo(), new FakePrefixResolver(), new PhoneNormalizer(),
                Map.of("CLARO", "12", "VIVO", "15"));
        List<InputFileReader> readers = List.of(new CsvInputReader());
        return new BatchExecutionService(readers, resolution, NO_METRICS,
                outDir.toString(), chunkSize, maxRows);
    }

    private static Path writeCsv(Path dir, String... phoneLines) throws IOException {
        Path file = dir.resolve("entrada.csv");
        StringBuilder sb = new StringBuilder("telefone\n");
        for (String l : phoneLines) {
            sb.append(l).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void happyPathCompletesJobWithStreamingCsvAndTotals(@TempDir Path tmp) throws IOException {
        Path in = writeCsv(tmp, "11999998888", "11999997777", "21999996666");
        BatchExecutionService svc = service(tmp, 1000, 1_000_000);
        BatchJob job = new BatchJob("job-1", "entrada.csv");

        svc.execute(job, in);

        assertEquals(BatchJob.Status.COMPLETED, job.getStatus(), job.getErrorMessage());
        assertEquals(3, job.getTotalRows());
        assertEquals(3, job.getProcessedRows());
        assertNull(job.getErrorMessage());
        assertNotNull(job.getResultPath());
        List<String> lines = Files.readAllLines(Path.of(job.getResultPath()), StandardCharsets.UTF_8);
        assertEquals(4, lines.size()); // header + 3 linhas
        assertEquals("telefone,operadora,fonte_consulta", lines.get(0));
        assertTrue(lines.get(1).contains("VIVO"),
                () -> "portabilidade esperada na linha 2: " + lines.get(1));
        // arquivo de upload é consumido/deletado ao final
        assertTrue(!Files.exists(in), "arquivo de entrada deve ser removido apos execucao");
    }

    @Test
    void missingPhoneColumnFailsJobWithoutOutput(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("sem-coluna.csv");
        Files.writeString(file, "numero\n11999998888\n", StandardCharsets.UTF_8);
        BatchExecutionService svc = service(tmp, 1000, 1_000_000);
        BatchJob job = new BatchJob("job-2", "sem-coluna.csv");

        svc.execute(job, file);

        assertEquals(BatchJob.Status.FAILED, job.getStatus());
        assertTrue(job.getErrorMessage().contains("telefone"), job.getErrorMessage());
        assertNull(job.getResultPath());
    }

    @Test
    void exceedingMaxRowsFailsJob(@TempDir Path tmp) throws IOException {
        Path in = writeCsv(tmp, "11999998888", "11999997777", "11999996666");
        BatchExecutionService svc = service(tmp, 1000, 2); // limite: 2 linhas
        BatchJob job = new BatchJob("job-3", "entrada.csv");

        svc.execute(job, in);

        assertEquals(BatchJob.Status.FAILED, job.getStatus());
        assertTrue(job.getErrorMessage().contains("limite"), job.getErrorMessage());
        assertNull(job.getResultPath());
    }

    @Test
    void unsupportedFormatFailsJob(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("planilha.pdf");
        Files.writeString(file, "x", StandardCharsets.UTF_8);
        BatchExecutionService svc = service(tmp, 1000, 1_000_000);
        BatchJob job = new BatchJob("job-4", "planilha.pdf");

        svc.execute(job, file);

        assertEquals(BatchJob.Status.FAILED, job.getStatus());
        assertTrue(job.getErrorMessage().contains("formato"), job.getErrorMessage());
    }
}
