package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Cliente devolvido pela API. tipo = "PF" ou "PJ"; nomeExibicao = nome social quando houver (RN-PF-06);
 * papeis = todos os papéis da pessoa (RN-PES-02); avisos = mensagens que não impedem a gravação (RN-PES-02, RN-CLI-03).
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
