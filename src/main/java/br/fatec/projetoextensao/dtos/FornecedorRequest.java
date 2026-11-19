package br.fatec.projetoextensao.dtos;

/**
 * Cadastro de fornecedor. ativo nasce 1 e não vem no corpo.
 */

public record FornecedorRequest(PessoaRequest pessoa, String cnpj, String prazoPagamento) {
}
