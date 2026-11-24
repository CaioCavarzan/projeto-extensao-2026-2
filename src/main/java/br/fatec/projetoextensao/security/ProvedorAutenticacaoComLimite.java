package br.fatec.projetoextensao.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.repositories.UsuarioRepository;
import br.fatec.projetoextensao.services.ControleTentativasLogin;
import br.fatec.projetoextensao.validacao.Textos;

/**
 * Confere login e senha (o "login" do sistema), com as regras de acesso:
 * É o único AuthenticationProvider do sistema: o Spring Security o usa para o login por HTTP Basic.
 */

@Component
public class ProvedorAutenticacaoComLimite implements AuthenticationProvider {

    public static final String MSG_INVALIDO = "Login ou senha inválidos.";
    public static final String MSG_BLOQUEADO = "Acesso bloqueado. Procure o administrador.";
    public static final String MSG_TENTATIVAS = "Muitas tentativas. Tente de novo em alguns minutos.";

    private final DaoAuthenticationProvider dao;
    private final ControleTentativasLogin tentativas;

    public ProvedorAutenticacaoComLimite(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                         ControleTentativasLogin tentativas) {
        this.tentativas = tentativas;
        UserDetailsService usuarios = login -> usuarioRepository.findByLoginIgnoreCase(login)
                .map(u -> User.withUsername(u.getLogin())
                        .password(u.getSenhaHash())
                        .roles(u.isAdmin() ? Perfis.PAPEL_ADMIN : Perfis.PAPEL_OPERADOR) 
                        .disabled(!u.isAtivo()) 
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(MSG_INVALIDO));
        this.dao = new DaoAuthenticationProvider();
        this.dao.setUserDetailsService(usuarios);
        this.dao.setPasswordEncoder(passwordEncoder);
        this.dao.setPreAuthenticationChecks(usuario -> { });
        this.dao.setPostAuthenticationChecks(usuario -> {
            if (!usuario.isEnabled()) {
                throw new DisabledException(MSG_BLOQUEADO);
            }
        });
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String login = Textos.normalizarLogin(authentication.getName());
        if (login == null) {
            throw new BadCredentialsException(MSG_INVALIDO);
        }
        if (tentativas.bloqueado(login)) {
            throw new LockedException(MSG_TENTATIVAS); 
        }
        try {
            Authentication resultado = dao.authenticate(
                    new UsernamePasswordAuthenticationToken(login, authentication.getCredentials()));
            tentativas.limpar(login);
            return resultado;
        } catch (BadCredentialsException e) {
            tentativas.registrarFalha(login); 
            throw new BadCredentialsException(MSG_INVALIDO); 
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
