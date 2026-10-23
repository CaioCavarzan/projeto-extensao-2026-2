package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Alteração de cliente. O CPF/CNPJ não muda por aqui: a correção é feita por um ADMIN em
 * PessoaService.corrigirCpf/corrigirCnpj, que mantém iguais os documentos dos outros papéis da pessoa (RN-PES-03/04/06).
 * ALTERADO (22/10/2026): sem prioridade (calculada pelo sistema, RN-CLI-09); se o front enviar o campo, é ignorado.
 * nomeSocial vale só para PF; nomeFantasia e ie valem só para PJ (os outros campos são ignorados).
 */
public record ClienteAtualizacaoRequest(
        PessoaRequest pessoa,
        String nomeSocial,
        String nomeFantasia,
        String ie,
        Integer fonteCodigo,
        String observacao) {
}
