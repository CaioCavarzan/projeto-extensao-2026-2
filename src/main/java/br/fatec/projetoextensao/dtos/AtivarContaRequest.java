package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Cadastre-se: ativação da conta de um funcionário já cadastrado (RN-USU-02).
 * is_admin não vem no corpo: o usuário nasce OPERADOR (RN-USU-03).
 */
public record AtivarContaRequest(
        String cpf,
        String login,
        String senha,
        String perguntaSeguranca,
        String respostaSeguranca) {
}
