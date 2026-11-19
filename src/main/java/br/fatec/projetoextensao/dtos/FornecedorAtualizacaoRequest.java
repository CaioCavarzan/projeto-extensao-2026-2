package br.fatec.projetoextensao.dtos;

/**
 * Alteração de fornecedor. O CNPJ é corrigido em PessoaService.corrigirCnpj; ativo muda em inativar/reativar.
 */

public record FornecedorAtualizacaoRequest(PessoaRequest pessoa, String prazoPagamento) {
}
