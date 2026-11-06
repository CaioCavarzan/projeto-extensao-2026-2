package br.fatec.projetoextensao.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.config.ParametrosSistema;

/**
 * INCLUÍDO (20/10/2026): RN-USU-08. Depois de N senhas erradas seguidas, as tentativas daquele login ficam
 * bloqueadas por alguns minutos (N e os minutos estão em fenix.login.* no application.properties).
 *
 * O contador fica na memória, por login, porque o banco não tem coluna para isso (a regra pede assim). Ele zera ao
 * reiniciar a aplicação e ao acertar a senha. O mesmo controle protege a resposta da pergunta de segurança na
 * redefinição de senha (RN-USU-05), com a chave "redefinir:" + login.
 *
 * Vale também para login que não existe: assim a resposta não revela quais logins existem (RN-USU-07).
 */
@Component
public class ControleTentativasLogin {

    private record Registro(int falhas, Instant bloqueadoAte) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final ParametrosSistema parametros;

    public ControleTentativasLogin(ParametrosSistema parametros) {
        this.parametros = parametros;
    }

    /** true quando a chave está bloqueada agora. */
    public boolean bloqueado(String chave) {
        Registro r = registros.get(chave);
        return r != null && r.bloqueadoAte() != null && Instant.now().isBefore(r.bloqueadoAte());
    }

    /** Conta uma falha; ao chegar no limite, bloqueia a chave pelos minutos configurados. */
    public void registrarFalha(String chave) {
        registros.compute(chave, (k, atual) -> {
            if (atual != null && atual.bloqueadoAte() != null && !expirou(atual)) {
                return atual; // já bloqueado: o bloqueio continua até o fim do prazo
            }
            int falhas = (atual == null || expirou(atual)) ? 1 : atual.falhas() + 1;
            if (falhas >= parametros.getMaxTentativasLogin()) {
                return new Registro(0, Instant.now().plus(Duration.ofMinutes(parametros.getMinutosBloqueioLogin())));
            }
            return new Registro(falhas, null);
        });
    }

    /** Acertou: zera o contador da chave. */
    public void limpar(String chave) {
        registros.remove(chave);
    }

    private boolean expirou(Registro r) {
        return r.bloqueadoAte() != null && !Instant.now().isBefore(r.bloqueadoAte());
    }
}
