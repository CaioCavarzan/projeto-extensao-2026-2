package br.fatec.projetoextensao.dtos;

/**
 * Meu perfil: dados do usuário logado e da sua pessoa. Nunca traz senha nem resposta de segurança.
 */

public record PerfilResponse(int funcionarioCodigo, String nome, String login, boolean admin, String cargo, String email, String telefone,
        String perguntaSeguranca) {
}
