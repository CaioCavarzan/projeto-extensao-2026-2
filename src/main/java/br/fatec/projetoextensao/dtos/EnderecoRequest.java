package br.fatec.projetoextensao.dtos;

/**
 * Endereço informado no cadastro da pessoa. O código é gerado pelo banco e
 * não vem no corpo. RN-END-03 (preencher logradouro, cidade e UF pelo CEP, com o ViaCEP) é do front;
 * aqui o back só valida o que chegar.
 */

public record EnderecoRequest(
        String uf,
        String cidade,
        String cep,
        String logradouro,
        String nrImovel) {
}
