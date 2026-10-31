package com.portabilidade.infrastructure.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Escaneia um diretório e retorna os arquivos com extensões suportadas (.csv e .xlsx).
 */
public class FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileScanner.class);

    /**
     * Lista todos os arquivos .csv e .xlsx no diretório especificado (não recursivo).
     *
     * @param directoryPath caminho do diretório de entrada
     * @return lista de caminhos absolutos como String
     */
    public List<String> scan(String directoryPath) {
        Path dir = Path.of(directoryPath);

        if (!Files.exists(dir)) {
            log.warn("Diretório de entrada não existe: {}", directoryPath);
            return Collections.emptyList();
        }

        if (!Files.isDirectory(dir)) {
            log.warn("Caminho não é um diretório: {}", directoryPath);
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<String> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".csv") || name.endsWith(".xlsx");
                    })
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .sorted()
                    .toList();

            if (files.isEmpty()) {
                log.warn("Nenhum arquivo .csv ou .xlsx encontrado em: {}", directoryPath);
            } else {
                log.info("Encontrado(s) {} arquivo(s) em '{}':", files.size(), directoryPath);
                files.forEach(f -> log.info("  → {}", f));
            }
            return files;

        } catch (IOException e) {
            log.error("Erro ao escanear diretório '{}': {}", directoryPath, e.getMessage());
            return Collections.emptyList();
        }
    }
}
