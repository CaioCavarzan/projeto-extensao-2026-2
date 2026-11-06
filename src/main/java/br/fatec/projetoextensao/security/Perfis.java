package br.fatec.projetoextensao.security;

/**
 * INCLUÍDO (20/10/2026): expressões de perfil usadas no @PreAuthorize dos Services (RN-SIS-02 e RN-SIS-03).
 *
 * Dois perfis, montados a partir de Usuarios.is_admin: ADMIN (is_admin = 1) e OPERADOR (is_admin = 0).
 * Cada método público de Service declara o perfil que exige, por exemplo:
 * <pre>
 *   &#64;PreAuthorize(Perfis.ADMIN)      // só administrador
 *   &#64;PreAuthorize(Perfis.QUALQUER)   // qualquer usuário logado (ADMIN ou OPERADOR)
 *   &#64;PreAuthorize(Perfis.PUBLICO)    // sem login: ativação de conta e redefinição de senha (RN-SIS-01)
 * </pre>
 * Só ADMIN (RN-SIS-03): gerenciar usuários e perfis; ver e alterar salário; excluir ou inativar cadastros;
 * alterar as listas de apoio (cores, etapas, fontes); cancelar OS; corrigir datas de etapa (RN-IRP-07);
 * corrigir CPF ou CNPJ.
 */
public final class Perfis {

    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String QUALQUER = "hasAnyRole('ADMIN', 'OPERADOR')";
    public static final String PUBLICO = "permitAll()";

    /**
     * INCLUÍDO (21/10/2026): mensagem do 403 (perfil sem permissão), igual à coluna "Mensagem ao usuário" da RN-SIS-03
     * na planilha. Neste sistema, a única ação negada a um usuário logado é a reservada ao ADMIN.
     */
    public static final String MSG_SO_ADMIN = "Ação permitida só para administradores.";

    /** Nomes dos papéis no Spring Security (o prefixo ROLE_ é acrescentado por ele). */
    public static final String PAPEL_ADMIN = "ADMIN";
    public static final String PAPEL_OPERADOR = "OPERADOR";

    private Perfis() {
    }
}
