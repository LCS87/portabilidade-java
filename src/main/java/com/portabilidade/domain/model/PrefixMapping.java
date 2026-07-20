package com.portabilidade.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para desserialização de cada entrada do prefixos_operadora.json.
 * Formato esperado:
 * {
 *   "1191000": { "operadora": "CLARO", "rn1": "55321", "qtd": 103 },
 *   ...
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrefixMapping {

    @JsonProperty("operadora")
    private String operadora;

    @JsonProperty("rn1")
    private String rn1;

    @JsonProperty("qtd")
    private int qtd;

    public PrefixMapping() {}

    public String getOperadora() {
        return operadora;
    }

    public void setOperadora(String operadora) {
        this.operadora = operadora;
    }

    public String getRn1() {
        return rn1;
    }

    public void setRn1(String rn1) {
        this.rn1 = rn1;
    }

    public int getQtd() {
        return qtd;
    }

    public void setQtd(int qtd) {
        this.qtd = qtd;
    }
}
