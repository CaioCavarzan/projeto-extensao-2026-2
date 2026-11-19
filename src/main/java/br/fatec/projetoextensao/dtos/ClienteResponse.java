package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * Cliente devolvido pela API. tipo = "PF" ou "PJ"; nomeExibicao = nome social quando houver;
 * papeis = todos os papéis da pessoa; avisos = mensagens que não impedem a gravação.
 */

public record ClienteResponse(
        int codigo,
        String tipo,
        String nome,
        String nomeExibicao,
        String email,
        String telefone,
        EnderecoResponse endereco,
        String cpf,
        String cnpj,
        String nomeSocial,
        String nomeFantasia,
        String ie,
        Integer fonteCodigo,
        String fonte,
        String observacao,
        Integer prioridade,
        List<String> papeis,
        List<String> avisos) {
}
