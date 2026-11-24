package br.fatec.projetoextensao.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.config.ParametrosSistema;

/**
 * Depois de N senhas erradas seguidas, as tentativas daquele login ficam
 * bloqueadas por alguns minutos (N e os minutos estão em fenix.login.* no application.properties).
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

    public boolean bloqueado(String chave) {
        Registro r = registros.get(chave);
        return r != null && r.bloqueadoAte() != null && Instant.now().isBefore(r.bloqueadoAte());
    }

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

    public void limpar(String chave) {
        registros.remove(chave);
    }

    private boolean expirou(Registro r) {
        return r.bloqueadoAte() != null && !Instant.now().isBefore(r.bloqueadoAte());
    }
}
