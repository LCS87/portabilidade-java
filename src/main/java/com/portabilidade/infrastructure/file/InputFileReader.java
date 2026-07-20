package com.portabilidade.infrastructure.file;

import java.util.List;
import java.util.Map;

/**
 * Interface (port) para leitura de arquivos de entrada (CSV ou Excel).
 * Cada linha do arquivo é representada como um Map de header → valor.
 */
public interface InputFileReader {

    /**
     * Lê o arquivo e retorna uma lista de linhas como mapas.
     * Os headers são normalizados (lowercase, trim, sem espaços).
     *
     * @param filePath caminho absoluto do arquivo
     * @return lista de mapas header → valor
     */
    List<Map<String, String>> read(String filePath);

    /**
     * Indica se esta implementação suporta o arquivo dado (por extensão).
     *
     * @param filePath caminho do arquivo
     * @return true se suportado
     */
    boolean supports(String filePath);
}
