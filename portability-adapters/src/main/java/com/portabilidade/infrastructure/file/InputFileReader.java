package com.portabilidade.infrastructure.file;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interface (port) para leitura de arquivos de entrada (CSV ou Excel).
 * Cada linha do arquivo é representada como um Map de header → valor.
 *
 * Fase 1 (PLANO_MELHORIAS): além da leitura em lista completa, os leitores
 * expõem {@link #stream(String)} — iteração linha-a-linha sem materializar
 * o arquivo inteiro em memória (suporte a arquivos maiores que o heap).
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
     * Itera o arquivo linha-a-linha (streaming), sem carregar tudo em memória.
     * Implementações devem liberar os recursos do arquivo quando o iterator
     * chega ao fim. Linhas em branco são ignoradas.
     */
    Iterator<Map<String, String>> stream(String filePath);

    /**
     * Indica se esta implementação suporta o arquivo dado (por extensão).
     *
     * @param filePath caminho do arquivo
     * @return true se suportado
     */
    boolean supports(String filePath);
}
