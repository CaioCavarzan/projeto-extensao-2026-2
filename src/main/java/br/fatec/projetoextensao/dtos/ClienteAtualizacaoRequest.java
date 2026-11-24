package br.fatec.projetoextensao.dtos;

/**
 * AAlteração de cliente. O CPF/CNPJ não muda por aqui: a correção é feita por um ADMIN em
 * PessoaService.corrigirCpf/corrigirCnpj, que mantém iguais os documentos dos outros papéis da pessoa.
 * Sem prioridade (calculada pelo sistema; se o front enviar o campo, é ignorado.
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
