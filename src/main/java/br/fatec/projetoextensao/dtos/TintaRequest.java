package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * INCLUÍDO (20/10/2026). Cadastro e alteração de tinta (RN-TIN-01 a RN-TIN-17). O estoque não vem aqui: muda só por ajuste (RN-TIN-14).
 * O código é gerado pelo banco (RN-TIN-03).
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
