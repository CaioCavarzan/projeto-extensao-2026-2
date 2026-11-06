package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Endereço informado no cadastro da pessoa (RN-END-05 a RN-END-10). O código é gerado pelo banco e
 * não vem no corpo (RN-END-04). RN-END-03 (preencher logradouro, cidade e UF pelo CEP, com o ViaCEP) é do front;
 * aqui o back só valida o que chegar.
 */
public record EnderecoRequest(
        String uf,
        String cidade,
        String cep,
        String logradouro,
        String nrImovel) {
}
