package br.fatec.projetoextensao.services;

import java.util.Optional;

/**
 * Quem está logado.
 *
 * A implementação usada pelo sistema é a UsuarioLogadoSpringSecurity (pacote security), que lê o login do contexto
 * de segurança do Spring. Os Services dependem só desta interface, então o usuário que registra uma OS ou um
 * cronograma nunca vem do corpo da requisição.
 */

public interface UsuarioLogado {

    Optional<String> login();

    boolean isAdmin();
}
