package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * INCLUÍDO (20/10/2026). Tinta devolvida pela API. unidadeEstoque = "kg" (RN-TIN-15); precoAConfirmar = valor_unit igual a 0 (RN-TIN-18).
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
