package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Dados comuns da pessoa (nome ou razão social, contato e endereço): RN-PES-09 a RN-PES-14.
 * Quando o CPF ou o CNPJ já pertence a outro papel da mesma pessoa (RN-PES-02), estes dados podem vir vazios:
 * os dados do cadastro existente são mantidos. O código da pessoa é gerado pelo banco (RN-PES-08).
 */
public record PessoaRequest(
        String nome,
        String email,
        String telefone,
        EnderecoRequest endereco) {
}
