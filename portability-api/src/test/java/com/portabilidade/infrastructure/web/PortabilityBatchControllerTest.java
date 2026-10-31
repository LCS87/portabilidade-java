package com.portabilidade.infrastructure.web;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import com.portabilidade.infrastructure.batch.BatchJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 2: contrato HTTP do batch (202 + job id, validação de formato,
 * status 404 para job inexistente) com o use case mockado.
 */
class PortabilityBatchControllerTest {

    private SubmitBatchUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(SubmitBatchUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PortabilityBatchController(useCase, "target/test-upload"))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void uploadReturns202WithJobIdAndLinks() throws Exception {
        when(useCase.submit(any(), anyString())).thenReturn("job-123");

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "clientes.csv", "text/csv",
                "telefone\n11999999999\n".getBytes());

        mockMvc.perform(multipart("/api/v1/portabilidade/batch").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("job-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.links.status").value("/api/v1/portabilidade/batch/job-123"));
    }

    @Test
    void unsupportedExtensionIsRejected415() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "malware.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/portabilidade/batch").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void emptyFileIsRejected400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("arquivo", "vazio.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/v1/portabilidade/batch").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownJobStatusIs404() throws Exception {
        when(useCase.status("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/portabilidade/batch/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void completedJobDownloadServesCsv() throws Exception {
        BatchJob job = new BatchJob("job-9", "clientes.csv");
        job.setStatus(BatchJob.Status.COMPLETED);
        java.nio.file.Path out = java.nio.file.Path.of("target/test-out/clientes_result.csv");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, "telefone;operadora;fonte_consulta\n");
        job.setResultPath(out.toString());
        when(useCase.status("job-9")).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/portabilidade/batch/job-9/resultado"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("resultado_portabilidade.csv")));
    }

    @Test
    void runningJobDownloadIs202WithProgress() throws Exception {
        BatchJob job = new BatchJob("job-10", "clientes.csv");
        job.setStatus(BatchJob.Status.RUNNING);
        job.addProcessed(1500);
        when(useCase.status("job-10")).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/portabilidade/batch/job-10/resultado"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.linhasProcessadas").value(1500));
    }

    // ByteArrayResource usado indiretamente pelo multipart mock — mantém import estável
    @SuppressWarnings("unused")
    private static final Class<?> KEEP = ByteArrayResource.class;
}
