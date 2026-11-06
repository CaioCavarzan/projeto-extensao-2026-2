package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * INCLUÍDO (20/10/2026). Ajuste de estoque (RN-TIN-14): quantidade positiva = entrada de compra; negativa = baixa. Em kg (RN-TIN-15).
 */
public record AjusteEstoqueRequest(BigDecimal quantidade) {
}
