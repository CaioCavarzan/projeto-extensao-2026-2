package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (21/10/2026). Meu perfil: troca da pergunta e da resposta de segurança, confirmada com a senha atual
 * (RN-USU-16 a RN-USU-18).
 */
public record AlterarPerguntaRequest(String senhaAtual, String perguntaSeguranca, String respostaSeguranca) {
}
