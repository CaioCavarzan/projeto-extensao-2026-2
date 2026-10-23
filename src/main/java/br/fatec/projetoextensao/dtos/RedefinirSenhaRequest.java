package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Lembrete de senha: login, resposta da pergunta de segurança e nova senha (RN-USU-05).
 */
public record RedefinirSenhaRequest(String login, String respostaSeguranca, String novaSenha) {
}
