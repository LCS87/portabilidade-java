package com.portabilidade.service;

import java.util.List;
import java.util.Map;

/**
 * Resultado do processamento de uma lista de números, indexado por POSIÇÃO (linha),
 * não pelo texto bruto do telefone.
 *
 * <p><b>Por quê?</b> A versão anterior usava {@code Map<String,String>} com chave =
 * número bruto. Linhas repetidas ou o mesmo telefone formatado de formas diferentes
 * ("(11) 98765-4321" vs "11987654321") colidiam na chave e perdiam resultados.
 * Como a lista de entrada vem na mesma ordem das linhas do arquivo, listas paralelas
 * indexadas por posição eliminam a colisão — correção da Fase 0/1 do PLANO_MELHORIAS.md.</p>
 *
 * <ul>
 *   <li>{@code rawNumbers[i]} — número como veio no arquivo (posição i)</li>
 *   <li>{@code operators[i]}  — operadora resolvida (ou "nao_identificado")</li>
 *   <li>{@code sources[i]}    — fonte da consulta: portabilidade | prefixo | nao_identificado | invalido</li>
 * </ul>
 */
public record ProcessResult(
        List<String> rawNumbers,
        List<String> operators,
        List<String> sources
) {
    public static final String NOT_IDENTIFIED = "nao_identificado";

    public ProcessResult {
        rawNumbers = rawNumbers == null ? List.of() : List.copyOf(rawNumbers);
        operators  = operators  == null ? List.of() : List.copyOf(operators);
        sources    = sources    == null ? List.of() : List.copyOf(sources);
        if (operators.size() != rawNumbers.size() || sources.size() != rawNumbers.size()) {
            throw new IllegalArgumentException(
                    "ProcessResult exige listas do mesmo tamanho: raw=%d, operators=%d, sources=%d"
                            .formatted(rawNumbers.size(), operators.size(), sources.size()));
        }
    }

    /** Operadora na posição i (com bounds check feito pelo get). */
    public String operatorAt(int i) {
        return operators.get(i);
    }

    /** Fonte da consulta na posição i. */
    public String sourceAt(int i) {
        return sources.get(i);
    }

    /** Total de linhas processadas. */
    public int size() {
        return rawNumbers.size();
    }

    /**
     * Busca por valor bruto para compatibilidade com chamadas pontuais.
     * Em caso de duplicatas ambíguas retorna a PRIMEIRA ocorrência — chamadores que
     * processam arquivos devem usar {@link #operatorAt(int)}/{@link #sourceAt(int)}.
     */
    public String operatorFor(String raw) {
        int i = indexOfRaw(raw);
        return i >= 0 ? operators.get(i) : null;
    }

    public String sourceFor(String raw) {
        int i = indexOfRaw(raw);
        return i >= 0 ? sources.get(i) : null;
    }

    private int indexOfRaw(String raw) {
        return rawNumbers.indexOf(raw);
    }

    /** Projeções em mapa (chave = primeira ocorrência do bruto) para logs/métricas. */
    public Map<String, String> toOperatorMap() {
        var m = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < rawNumbers.size(); i++) {
            m.putIfAbsent(rawNumbers.get(i), operators.get(i));
        }
        return m;
    }

    public Map<String, String> toSourceMap() {
        var m = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < rawNumbers.size(); i++) {
            m.putIfAbsent(rawNumbers.get(i), sources.get(i));
        }
        return m;
    }
}
