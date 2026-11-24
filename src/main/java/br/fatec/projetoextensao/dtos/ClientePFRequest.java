package br.fatec.projetoextensao.dtos;

/**
 * Cadastro de cliente pessoa física.
 * fonteCodigo vazio = fonte 'Não informada'.
 * Sem prioridade. Ela é calculada pelo sistema (PrioridadeClienteService); se o
 * front ainda enviar o campo, ele é ignorado.
 */

public record ClientePFRequest(
        PessoaRequest pessoa,
        String cpf,
        String nomeSocial,
        Integer fonteCodigo,
        String observacao) {
}
