package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * Cadastro e alteração de tinta. O estoque não vem aqui: muda só por ajuste.
 * O código é gerado pelo banco.
 */

public record TintaRequest(
        Integer fornecedorCodigo,
        Integer corCodigo,
        String descricao,
        String tipo,
        String acabamento,
        String referenciaFornecedor,
        BigDecimal valorUnitario) {
}
