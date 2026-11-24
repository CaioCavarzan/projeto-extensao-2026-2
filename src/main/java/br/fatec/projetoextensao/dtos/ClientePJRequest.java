package br.fatec.projetoextensao.dtos;

/**
 * Cadastro de cliente pessoa jurídica.
 * fonteCodigo vazio = fonte 'Não informada'.
 * Sem prioridade. Ela é calculada pelo sistema (PrioridadeClienteService); se o
 * front ainda enviar o campo, ele é ignorado.
 */

public record ClientePJRequest(
        PessoaRequest pessoa,
        String cnpj,
        String nomeFantasia,
        String ie,
        Integer fonteCodigo,
        String observacao) {
}
