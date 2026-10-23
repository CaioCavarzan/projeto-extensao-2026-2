package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (21/10/2026). Meu perfil: dados do usuário logado e da sua pessoa. Nunca traz senha nem resposta de segurança (RN-USU-14).
 */
public record PerfilResponse(int funcionarioCodigo, String nome, String login, boolean admin, String cargo, String email, String telefone,
        String perguntaSeguranca) {
}
