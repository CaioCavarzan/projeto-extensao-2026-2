package br.fatec.projetoextensao.dtos;

/**
 * Lembrete de senha: login, resposta da pergunta de segurança e nova senha.
 */

public record RedefinirSenhaRequest(String login, String respostaSeguranca, String novaSenha) {
}
