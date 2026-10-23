package br.fatec.projetoextensao.services;

import java.util.Optional;

/**
 * INCLUÍDO (20/10/2026): quem está logado (RN-SIS-08).
 *
 * A implementação usada pelo sistema é a UsuarioLogadoSpringSecurity (pacote security), que lê o login do contexto
 * de segurança do Spring. Os Services dependem só desta interface, então o usuário que registra uma OS ou um
 * cronograma nunca vem do corpo da requisição.
 */
public interface UsuarioLogado {

    /** Login do usuário autenticado, ou vazio quando ninguém está logado. */
    Optional<String> login();

    /** true quando o usuário logado tem o perfil ADMIN (usado, por exemplo, para mostrar o salário: RN-FUN-09). */
    boolean isAdmin();
}
