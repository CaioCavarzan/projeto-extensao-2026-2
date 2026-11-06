package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Alteração de fornecedor. O CNPJ é corrigido em PessoaService.corrigirCnpj; ativo muda em inativar/reativar.
 */
public record FornecedorAtualizacaoRequest(PessoaRequest pessoa, String prazoPagamento) {
}
