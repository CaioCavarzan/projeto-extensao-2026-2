package br.fatec.projetoextensao.dtos;

/**
 * Cadastre-se: ativação da conta de um funcionário já cadastrado.
 * is_admin não vem no corpo: o usuário nasce OPERADOR.
 */

public record AtivarContaRequest(
        String cpf,
        String login,
        String senha,
        String perguntaSeguranca,
        String respostaSeguranca) {
}
