package br.fatec.projetoextensao.dtos;

/**
 * Endereço devolvido pela API.
 */

public record EnderecoResponse(
        int codigo,
        String uf,
        String cidade,
        String cep,
        String logradouro,
        String nrImovel) {
}
