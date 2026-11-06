package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Usuário devolvido pela API: nunca traz senha_hash nem resposta_hash (RN-USU-14).
 */
public record UsuarioResponse(
        int funcionarioCodigo,
        String nome,
        String login,
        boolean admin,
        boolean ativo,
        String perguntaSeguranca) {
}
