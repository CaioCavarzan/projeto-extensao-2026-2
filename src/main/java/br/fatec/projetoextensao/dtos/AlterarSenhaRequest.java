package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Troca de senha pelo próprio usuário logado.
 */
public record AlterarSenhaRequest(String senhaAtual, String novaSenha) {
}
