package com.portabilidade.bff.interfaces.rest;

import com.portabilidade.bff.application.BatchService;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Endpoints da tela de upload/batch: submit, progresso (SSE + polling) e download. */
@RestController
@RequestMapping("/bff/v1/batch")
public class BatchController {

    private static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024; // 50 MB

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> submit(@RequestPart("arquivo") FilePart file) {
        return readAll(file).flatMap(content ->
                batchService.submit(content, file.filename()));
    }

    /** SSE: emite o status a cada 2s até COMPLETED/FAILED. */
    @GetMapping(value = "/{id}/progresso", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> progresso(@PathVariable String id) {
        return batchService.monitor(id);
    }

    /** Polling simples (alternativa ao SSE para clients mais antigos). */
    @GetMapping("/{id}")
    public Mono<Map<String, Object>> status(@PathVariable String id) {
        return batchService.monitor(id).take(1).single();
    }

    @GetMapping("/{id}/resultado")
    public Mono<ResponseEntity<byte[]>> resultado(@PathVariable String id) {
        return batchService.download(id).map(bytes -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resultado_portabilidade.csv\"")
                .body(bytes));
    }

    private static Mono<byte[]> readAll(FilePart file) {
        return file.content()
                .map(buffer -> {
                    byte[] dst = new byte[buffer.readableByteCount()];
                    buffer.toByteBuffer().get(dst);
                    DataBufferUtils.release(buffer);
                    return dst;
                })
                .reduce(new byte[0], BatchController::concat)
                .handle((b, sink) -> {
                    if (b.length > MAX_UPLOAD_BYTES) {
                        sink.error(new IllegalArgumentException("arquivo excede 50 MB"));
                    } else {
                        sink.next(b);
                    }
                });
    }

    private static byte[] concat(byte[] a, byte[] buf) {
        byte[] out = new byte[a.length + buf.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(buf, 0, out, a.length, buf.length);
        return out;
    }
}
