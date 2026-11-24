package br.fatec.projetoextensao.dtos;

/**
 * Dados comuns da pessoa (nome ou razão social, contato e endereço).
 * Quando o CPF ou o CNPJ já pertence a outro papel da mesma pessoa, estes dados podem vir vazios:
 * os dados do cadastro existente são mantidos. O código da pessoa é gerado pelo banco.
 */

public record PessoaRequest(
        String nome,
        String email,
        String telefone,
        EnderecoRequest endereco) {
}
