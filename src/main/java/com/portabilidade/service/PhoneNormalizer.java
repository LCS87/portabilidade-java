package com.portabilidade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Normaliza números de telefone brasileiros para o formato canônico usado na base de portabilidade.
 *
 * A base de portabilidade armazena os números EXATAMENTE como foram cadastrados:
 *   - Celulares: 11 dígitos (DDD + 9 + 8 dígitos)
 *   - Fixos:     10 dígitos (DDD + 8 dígitos)
 *   - 0800:      9 a 11 dígitos
 *
 * Por isso NÃO inserimos o "9" automaticamente em números de 10 dígitos: isso
 * transformaria um fixo válido em um celular inexistente e destruiria a correspondência.
 *
 * Regras aplicadas:
 * 1. Remove todos os caracteres não-numéricos.
 * 2. Remove DDI "55" se presente e o resto ficar com 10 ou 11 dígitos.
 * 3. Aceita 10 ou 11 dígitos como válidos (não força o 9).
 * 4. Qualquer outro comprimento retorna null.
 */
public class PhoneNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PhoneNormalizer.class);

    /**
     * Normaliza o número bruto e retorna como Long, ou null se inválido.
     *
     * @param raw número bruto (pode conter +, -, espaços, parênteses)
     * @return Long com 10 ou 11 dígitos, ou null
     */
    public Long normalize(String raw) {
        String digits = getCleanDigits(raw);
        if (digits == null) return null;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            log.debug("Falha ao converter dígitos '{}' para Long: {}", digits, e.getMessage());
            return null;
        }
    }

    /**
     * Retorna a string limpa com 10 ou 11 dígitos (forma canônica da base), ou null se inválido.
     *
     * @param raw número bruto
     * @return String de 10 ou 11 dígitos, ou null
     */
    public String getCleanDigits(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // Remove tudo que não for dígito
        String digits = raw.replaceAll("[^0-9]", "");

        if (digits.isEmpty()) return null;

        // Remove DDI 55 se o resto ficar com 10 ou 11 dígitos (válido para a base)
        if (digits.startsWith("55") && digits.length() >= 12 && digits.length() <= 13) {
            digits = digits.substring(2);
        }

        // Aceita 10 (fixo) ou 11 (celular) dígitos — NÃO insere o 9 automaticamente.
        if (digits.length() == 10 || digits.length() == 11) {
            return digits;
        }

        log.debug("Número '{}' resultou em '{}' dígitos após normalização — ignorado", raw, digits.length());
        return null;
    }
}
