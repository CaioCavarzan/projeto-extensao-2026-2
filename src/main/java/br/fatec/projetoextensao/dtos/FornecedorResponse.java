package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * Fornecedor devolvido pela API.
 */

public record FornecedorResponse(
        int codigo,
        String nome,
        String email,
        String telefone,
        EnderecoResponse endereco,
        String cnpj,
        String prazoPagamento,
        boolean ativo,
        List<String> papeis,
        List<String> avisos) {
}
