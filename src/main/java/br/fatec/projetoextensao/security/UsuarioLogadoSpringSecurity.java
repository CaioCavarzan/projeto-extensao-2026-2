package br.fatec.projetoextensao.security;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.services.UsuarioLogado;

/**
 * INCLUÍDO (20/10/2026): lê do contexto de segurança do Spring quem fez a requisição (RN-SIS-08).
 * Os Services usam a interface UsuarioLogado e nunca recebem o usuário pelo corpo da requisição.
 */
@Component
public class UsuarioLogadoSpringSecurity implements UsuarioLogado {

    @Override
    public Optional<String> login() {
        Authentication autenticacao = autenticacao();
        return autenticacao == null ? Optional.empty() : Optional.ofNullable(autenticacao.getName());
    }

    @Override
    public boolean isAdmin() {
        Authentication autenticacao = autenticacao();
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Perfis.PAPEL_ADMIN).equals(a.getAuthority()));
    }

    private Authentication autenticacao() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return a;
    }
}
