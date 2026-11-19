package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * Ajuste de estoque: quantidade positiva = entrada de compra; negativa = baixa. Em kg.
 */

public record AjusteEstoqueRequest(BigDecimal quantidade) {
}
