package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.repositories.ClienteRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (22/10/2026): prioridade do cliente, calculada pelo sistema (RN-CLI-09, decisão da Fênix de 22/10).
 *
 * Regra: "cliente com maior faturamento = maior prioridade", em escala de 0 a 100.
 * <ul>
 *   <li>faturamento do cliente = soma de valor_os das OS dele que não estão canceladas, em todo o histórico
 *       (a OS cancelada deixa de valer; OS em andamento conta, como já faz a tela de Clientes);</li>
 *   <li>prioridade = faturamento do cliente ÷ maior faturamento entre os clientes × 100, arredondada para o inteiro
 *       mais próximo (metade para cima);</li>
 *   <li>o cliente de maior faturamento fica com 100; quem não tem OS (ou só tem OS cancelada ou sem valor) fica
 *       com 0; se ninguém faturou ainda, todos ficam com 0.</li>
 * </ul>
 * Os limites vêm de fenix.prioridade.minima e fenix.prioridade.maxima (0 e 100): a fórmula é
 * mínimo + faturamento ÷ maior × (máximo − mínimo).
 *
 * O valor não é digitado nem gravado: é recalculado a cada consulta, então acompanha sozinho as OS abertas,
 * canceladas e alteradas. A coluna Clientes.prioridade deixa de ser usada pela aplicação (fica NULL); na Etapa 3 o
 * mesmo cálculo pode virar uma view ou uma procedure, se a Fênix quiser o valor gravado no banco.
 *
 * A prioridade da OS (RN-OS-16 e RN-OS-17) nasce com o valor do cliente no momento da abertura e pode ser mudada
 * à mão; ela não é recalculada depois.
 */
@Service
@RequiredArgsConstructor
public class PrioridadeClienteService {

    private final ClienteRepository clienteRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final StatusService statusService;
    private final ParametrosSistema parametros;

    /** Prioridade de todos os clientes (código do cliente -> prioridade), calculada de uma vez. */
    @Transactional(readOnly = true)
    Map<Integer, Integer> calcularTodas() {
        Map<Integer, BigDecimal> faturamento = new HashMap<>();
        for (OrdemServicoEntity os : ordemServicoRepository.findAll()) {
            if (statusService.statusAtual(os) == StatusOsEnum.CANCELADA) {
                continue;
            }
            BigDecimal valor = os.getValorOS() == null ? BigDecimal.ZERO : os.getValorOS();
            faturamento.merge(os.getCliente().getPessoaCodigo(), valor, BigDecimal::add);
        }
        BigDecimal maior = faturamento.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        int minimo = parametros.getPrioridadeMinima();
        BigDecimal amplitude = BigDecimal.valueOf((long) parametros.getPrioridadeMaxima() - minimo);
        Map<Integer, Integer> prioridades = new HashMap<>();
        for (ClienteEntity c : clienteRepository.findAll()) {
            BigDecimal f = faturamento.getOrDefault(c.getPessoaCodigo(), BigDecimal.ZERO);
            int prioridade = minimo;
            if (maior.signum() > 0 && f.signum() > 0) {
                prioridade = minimo + f.multiply(amplitude).divide(maior, 0, RoundingMode.HALF_UP).intValue();
            }
            prioridades.put(c.getPessoaCodigo(), prioridade);
        }
        return prioridades;
    }

    /** Prioridade de um cliente. */
    @Transactional(readOnly = true)
    int deCliente(ClienteEntity cliente) {
        return calcularTodas().getOrDefault(cliente.getPessoaCodigo(), parametros.getPrioridadeMinima());
    }
}
