package br.fatec.projetoextensao.services;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
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
 *
 * ALTERADO (18/11/2026) [E3-10]: a Etapa 3 transformou esse cálculo na função dbo.fn_prioridade_clientes
 * (functions.sql), com a MESMA fórmula: 1ª passada soma valor_os das OS não canceladas de cada cliente
 * numa variável tabela; 2ª passada aplica mínimo + ROUND(faturamento × amplitude ÷ maior, 0). O Java deixou de
 * percorrer todas as OS (uma consulta de status por OS) e passou a só ler o resultado da função, passando os
 * limites do application.properties. O arredondamento ROUND(x, 0) do SQL Server é "metade para longe do zero", igual
 * ao HALF_UP que o Java usava (para valores positivos), então os resultados não mudam (ex.: 0,5 -> 1; 33,33 -> 33).
 */
@Service
@RequiredArgsConstructor
public class PrioridadeClienteService {

    // ALTERADO (18/11/2026) [E3-10]: eram ClienteRepository, OrdemServicoRepository e StatusService, usados no cálculo
    // em Java. Agora basta a função do banco (RotinasBancoRepository) e os limites da escala (ParametrosSistema).
    private final RotinasBancoRepository rotinasBanco;
    private final ParametrosSistema parametros;

    /**
     * Prioridade de todos os clientes (código do cliente -> prioridade), calculada de uma vez.
     * ALTERADO (18/11/2026) [E3-10]: era o laço em Java sobre ordemServicoRepository.findAll() e
     * clienteRepository.findAll(); agora é uma chamada a dbo.fn_prioridade_clientes(mínimo, máximo).
     */
    @Transactional(readOnly = true)
    Map<Integer, Integer> calcularTodas() {
        return rotinasBanco.prioridadesClientes(parametros.getPrioridadeMinima(), parametros.getPrioridadeMaxima());
    }

    /**
     * Prioridade de um cliente.
     * ALTERADO (18/11/2026) [E3-10]: era calcularTodas().get(cliente); agora a função é filtrada pelo cliente no próprio
     * SQL (where cliente_codigo = ?), o que devolve uma linha em vez da lista inteira.
     */
    @Transactional(readOnly = true)
    int deCliente(ClienteEntity cliente) {
        return rotinasBanco.prioridadeCliente(cliente.getPessoaCodigo(), parametros.getPrioridadeMinima(),
                parametros.getPrioridadeMaxima()).orElse(parametros.getPrioridadeMinima());
    }
}
