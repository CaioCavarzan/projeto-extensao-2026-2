package br.fatec.projetoextensao.dtos;

/**
 * Tinta aplicada no item, definida pela produção. Vazio = retirar a tinta.
 */

public record TintaItemRequest(Integer tintaCodigo) {
}
