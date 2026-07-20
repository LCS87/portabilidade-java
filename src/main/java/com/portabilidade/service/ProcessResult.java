package com.portabilidade.service;

import java.util.Map;

/**
 * Resultado do processamento de uma lista de números.
 * operatorMap : número original → nome da operadora (ou "nao_identificado")
 * sourceMap   : número original → fonte da consulta ("portabilidade", "prefixo", "nao_identificado", "invalido")
 */
public record ProcessResult(
        Map<String, String> operatorMap,
        Map<String, String> sourceMap
) {}
