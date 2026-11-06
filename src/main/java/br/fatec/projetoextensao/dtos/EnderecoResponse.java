package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Endereço devolvido pela API.
 */
public record EnderecoResponse(
        int codigo,
        String uf,
        String cidade,
        String cep,
        String logradouro,
        String nrImovel) {
}
