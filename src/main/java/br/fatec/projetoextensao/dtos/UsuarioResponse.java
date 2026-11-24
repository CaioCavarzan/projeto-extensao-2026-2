package br.fatec.projetoextensao.dtos;

/**
 * Usuário devolvido pela API: nunca traz senha_hash nem resposta_hash.
 */

public record UsuarioResponse(
        int funcionarioCodigo,
        String nome,
        String login,
        boolean admin,
        boolean ativo,
        String perguntaSeguranca) {
}
