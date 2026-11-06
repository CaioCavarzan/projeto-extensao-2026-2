package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Cadastro de fornecedor (RN-FOR-01 a RN-FOR-09). ativo nasce 1 (RN-FOR-07) e não vem no corpo.
 */
public record FornecedorRequest(PessoaRequest pessoa, String cnpj, String prazoPagamento) {
}
