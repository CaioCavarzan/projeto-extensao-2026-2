package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (21/10/2026). Meu perfil: contatos que o próprio usuário pode alterar (e-mail e telefone da sua pessoa).
 * O nome e o endereço continuam no cadastro de funcionários, feito pelo ADMIN.
 */
public record PerfilRequest(String email, String telefone) {
}
