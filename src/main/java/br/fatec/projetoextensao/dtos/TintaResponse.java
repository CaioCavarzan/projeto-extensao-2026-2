package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * Tinta devolvida pela API. unidadeEstoque = "kg"; precoAConfirmar = valor_unit igual a 0.
 */

public record TintaResponse(
        int codigo,
        int fornecedorCodigo,
        String fornecedorNome,
        boolean fornecedorAtivo,
        int corCodigo,
        String corNome,
        String descricao,
        String tipo,
        String acabamento,
        String referenciaFornecedor,
        BigDecimal estoqueAtual,
        String unidadeEstoque,
        BigDecimal valorUnitario,
        boolean precoAConfirmar) {
}
