package br.fatec.projetoextensao.services;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import lombok.RequiredArgsConstructor;

/**
 * Prioridade do cliente, calculada pelo sistema.
 */

@Service
@RequiredArgsConstructor
public class PrioridadeClienteService {

    private final RotinasBancoRepository rotinasBanco;
    private final ParametrosSistema parametros;

    @Transactional(readOnly = true)
    Map<Integer, Integer> calcularTodas() {
        return rotinasBanco.prioridadesClientes(parametros.getPrioridadeMinima(), parametros.getPrioridadeMaxima());
    }

    @Transactional(readOnly = true)
    int deCliente(ClienteEntity cliente) {
        return rotinasBanco.prioridadeCliente(cliente.getPessoaCodigo(), parametros.getPrioridadeMinima(),
                parametros.getPrioridadeMaxima()).orElse(parametros.getPrioridadeMinima());
    }
}
