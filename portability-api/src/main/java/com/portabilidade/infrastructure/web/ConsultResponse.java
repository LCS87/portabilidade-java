package com.portabilidade.infrastructure.web;

import java.util.List;

/**
 * Resposta do consultar: um item por número solicitado, na mesma ordem.
 * fonte: portabilidade | prefixo | nao_identificado | invalido
 */
public record ConsultResponse(List<Item> itens) {

    public record Item(String numero, String telefoneNormalizado, String operadora, String fonte) {
    }
}
