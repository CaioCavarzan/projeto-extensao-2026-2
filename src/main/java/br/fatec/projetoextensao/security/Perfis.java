package br.fatec.projetoextensao.security;

/**
 * Expressões de perfil usadas no @PreAuthorize dos Services.
 *
 * Dois perfis, montados a partir de Usuarios.is_admin: ADMIN (is_admin = 1) e OPERADOR (is_admin = 0).
 * Cada método público de Service declara o perfil que exige, por exemplo:
 * <pre>
 *   &#64;PreAuthorize(Perfis.ADMIN)      // só administrador
 *   &#64;PreAuthorize(Perfis.QUALQUER)   // qualquer usuário logado (ADMIN ou OPERADOR)
 *   &#64;PreAuthorize(Perfis.PUBLICO)    // sem login: ativação de conta e redefinição de senha
 * </pre>
 * Só ADMIN (RN-SIS-03): gerenciar usuários e perfis; ver e alterar salário; excluir ou inativar cadastros;
 * alterar as listas de apoio (cores, etapas, fontes); cancelar OS; corrigir datas de etapa;
 * corrigir CPF ou CNPJ.
 */

public final class Perfis {

    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String QUALQUER = "hasAnyRole('ADMIN', 'OPERADOR')";
    public static final String PUBLICO = "permitAll()";

    public static final String MSG_SO_ADMIN = "Ação permitida só para administradores.";

    public static final String PAPEL_ADMIN = "ADMIN";
    public static final String PAPEL_OPERADOR = "OPERADOR";

    private Perfis() {
    }
}
