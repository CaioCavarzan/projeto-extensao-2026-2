package br.fatec.projetoextensao.dtos;

/**
 * Troca de senha pelo próprio usuário logado.
 */

public record AlterarSenhaRequest(String senhaAtual, String novaSenha) {
}
