package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Cadastro de cliente pessoa física (RN-CLI-01, RN-PF-01 a RN-PF-05).
 * fonteCodigo vazio = fonte 'Não informada' (RN-CLI-06).
 * ALTERADO (22/10/2026): sem prioridade. Ela é calculada pelo sistema (PrioridadeClienteService, RN-CLI-09); se o
 * front ainda enviar o campo, ele é ignorado.
 */
public record ClientePFRequest(
        PessoaRequest pessoa,
        String cpf,
        String nomeSocial,
        Integer fonteCodigo,
        String observacao) {
}
