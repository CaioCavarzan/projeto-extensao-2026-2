package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Cadastro de cliente pessoa jurídica (RN-CLI-01, RN-PJ-01 a RN-PJ-07).
 * fonteCodigo vazio = fonte 'Não informada' (RN-CLI-06).
 * ALTERADO (22/10/2026): sem prioridade. Ela é calculada pelo sistema (PrioridadeClienteService, RN-CLI-09); se o
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
