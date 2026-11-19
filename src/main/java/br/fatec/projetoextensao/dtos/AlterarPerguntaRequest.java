package br.fatec.projetoextensao.dtos;

/**
 * Meu perfil: troca da pergunta e da resposta de segurança, confirmada com a senha atual.
 */

public record AlterarPerguntaRequest(String senhaAtual, String perguntaSeguranca, String respostaSeguranca) {
}
