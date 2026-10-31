package com.portabilidade.infrastructure.web;

import com.portabilidade.application.usecase.SubmitBatchUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fatia web (Fase 2): sobe o contexto MVC real — validação Bean Validation,
 * conversores Jackson e ProblemDetail do ResponseEntityExceptionHandler —
 * sem os adaptadores pesados (DuckDB/JSON), que ficam mockados.
 */
@WebMvcTest(controllers = {PortabilityController.class, PortabilityBatchController.class})
@Import({PortabilityController.class, PortabilityBatchController.class, ApiExceptionHandler.class})
class ApiWebLayerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitBatchUseCase submitBatchUseCase;

    @Test
    void consultWithNullBodyIs400ViaRealValidationPipeline() throws Exception {
        mockMvc.perform(post("/api/v1/portabilidade/consultar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void batchUploadGoesThroughRealDispatcherAndReturns202() throws Exception {
        when(submitBatchUseCase.submit(any(), anyString())).thenReturn("job-real-1");

        MockMultipartFile file = new MockMultipartFile(
                "arquivo", "base.csv", "text/csv", "telefone\n".getBytes());

        mockMvc.perform(multipart("/api/v1/portabilidade/batch").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("job-real-1"));
    }

    @Test
    void unknownJobIs404ViaResponseStatusExceptionMapping() throws Exception {
        when(submitBatchUseCase.status("zzz")).thenReturn(Optional.empty());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/portabilidade/batch/zzz"))
                .andExpect(status().isNotFound());
    }
}
