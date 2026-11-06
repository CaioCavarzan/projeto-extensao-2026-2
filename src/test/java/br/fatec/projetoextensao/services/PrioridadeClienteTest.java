package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.OsResponse;

/**
 * INCLUÍDO (22/10/2026): valores definidos pela Fênix em 22/10 — prioridade do cliente de 0 a 100 calculada pelo
 * faturamento (RN-CLI-09), prioridade da OS na mesma escala (RN-OS-16 e RN-OS-17) e unidades do item (RN-ITE-17).
 *
 * Os valores das OS são grandes de propósito: o banco de teste pode ter OS de outros testes, e a prioridade é
 * relativa ao maior faturamento de todos os clientes; assim, o cliente de maior valor aqui é sempre o maior.
 */
class PrioridadeClienteTest extends TesteBase {

    @Autowired
    private ParametrosSistema parametros;

    private ClienteResponse clienteComOs(String nome, String valor) {
        ClienteResponse c = novoClientePF(nome + " " + System.nanoTime());
        abrirOs(c.codigo(), itemJateamento("Peça", new BigDecimal(valor)));
        return c;
    }

    private int prioridadeDe(ClienteResponse c) {
        return clienteService.buscar(c.codigo()).prioridade();
    }

    /** Os valores do application.properties chegam certos (inclusive o Ç de PEÇA, escrito como Ç no arquivo). */
    @Test
    void parametrosDefinidosEm22_10() {
        assertThat(parametros.getPrioridadeMinima()).isEqualTo(0);
        assertThat(parametros.getPrioridadeMaxima()).isEqualTo(100);
        assertThat(parametros.getUnidadesItem()).containsExactly("PEÇA", "CONJUNTO", "KG", "METRO", "JOGO");
    }

    /** RN-CLI-09: cliente sem nenhuma OS fica com 0. */
    @Test
    void rnCli09_semOsFicaComZero() {
        clienteComOs("Grande", "400000");
        assertThat(prioridadeDe(novoClientePF("Sem OS " + System.nanoTime()))).isEqualTo(0);
    }

    /** RN-CLI-09: maior faturamento = 100; os outros em proporção (100.000 ÷ 400.000 = 25). */
    @Test
    void rnCli09_maiorFaturamentoFicaCom100() {
        ClienteResponse a = clienteComOs("A", "400000");
        ClienteResponse b = clienteComOs("B", "100000");
        assertThat(prioridadeDe(a)).isEqualTo(100);
        assertThat(prioridadeDe(b)).isEqualTo(25);
    }

    /** O faturamento do cliente soma todas as OS dele (não só a maior). */
    @Test
    void rnCli09_somaAsOsDoCliente() {
        ClienteResponse a = clienteComOs("A", "400000");
        ClienteResponse b = clienteComOs("B", "100000");
        abrirOs(b.codigo(), itemJateamento("Outra", new BigDecimal("100000"))); // B passa a faturar 200.000
        assertThat(prioridadeDe(a)).isEqualTo(100);
        assertThat(prioridadeDe(b)).isEqualTo(50);
    }

    /** Arredondamento para o inteiro mais próximo, metade para cima (33,33 -> 33; 0,5 -> 1). */
    @Test
    void rnCli09_arredondaParaOInteiroMaisProximo() {
        ClienteResponse a = clienteComOs("A", "300000");
        ClienteResponse b = clienteComOs("B", "100000");
        assertThat(prioridadeDe(a)).isEqualTo(100);
        assertThat(prioridadeDe(b)).isEqualTo(33);

        ClienteResponse c = clienteComOs("C", "1500"); // 1.500 ÷ 300.000 × 100 = 0,5
        assertThat(prioridadeDe(c)).isEqualTo(1);
    }

    /** OS cancelada não conta como faturamento. */
    @Test
    void rnCli09_osCanceladaNaoConta() {
        ClienteResponse a = novoClientePF("Cancelou " + System.nanoTime());
        OsResponse osA = abrirOs(a.codigo(), itemJateamento("Peça", new BigDecimal("900000")));
        ClienteResponse b = clienteComOs("B", "100000");
        assertThat(prioridadeDe(a)).isEqualTo(100); // enquanto a OS vale, A é o maior
        assertThat(prioridadeDe(b)).isEqualTo(11);  // 100.000 ÷ 900.000 × 100 = 11,1

        ordemServicoService.cancelar(osA.numero());
        assertThat(prioridadeDe(a)).isEqualTo(0);
        assertThat(prioridadeDe(b)).isEqualTo(100);
    }

    /** OS sem valor (item sem valor, RN-OS-15) não dá prioridade. */
    @Test
    void rnCli09_osSemValorFicaComZero() {
        ClienteResponse a = clienteComOs("Grande", "400000");
        ClienteResponse semValor = novoClientePF("Sem valor " + System.nanoTime());
        abrirOs(semValor.codigo(), itemJateamento("Peça", null));
        assertThat(prioridadeDe(a)).isEqualTo(100);
        assertThat(prioridadeDe(semValor)).isEqualTo(0);
    }

    /** A listagem de clientes traz a prioridade calculada de cada um. */
    @Test
    void rnCli09_listaTrazAPrioridadeCalculada() {
        ClienteResponse a = clienteComOs("A", "400000");
        ClienteResponse b = clienteComOs("B", "100000");
        List<ClienteResponse> lista = clienteService.listar();
        assertThat(lista.stream().filter(c -> c.codigo() == a.codigo()).findFirst().orElseThrow().prioridade()).isEqualTo(100);
        assertThat(lista.stream().filter(c -> c.codigo() == b.codigo()).findFirst().orElseThrow().prioridade()).isEqualTo(25);
    }

    /** RN-OS-17: sem prioridade informada, a OS nasce com a prioridade calculada do cliente. */
    @Test
    void rnOs17_osNasceComAPrioridadeDoCliente() {
        clienteComOs("A", "400000");
        ClienteResponse b = clienteComOs("B", "100000");
        OsResponse nova = abrirOs(b.codigo(), itemJateamento("Mais uma", null));
        assertThat(nova.prioridade()).isEqualTo(25);
    }

    /** RN-OS-16 e RN-OS-17: a prioridade da OS pode ser mudada à mão, de 0 a 100; fora disso é recusada. */
    @Test
    void rnOs16_prioridadeManualDe0a100() {
        ClienteResponse c = novoClientePF("Manual " + System.nanoTime());
        OsResponse os = ordemServicoService.abrir(new AbrirOsRequest(c.codigo(), 0, List.of(itemJateamento("A", null))));
        assertThat(os.prioridade()).isEqualTo(0);
        assertThat(ordemServicoService.alterarPrioridade(os.numero(), 100).prioridade()).isEqualTo(100);
        assertThat(ordemServicoService.alterarPrioridade(os.numero(), 57).prioridade()).isEqualTo(57);
        assertThat(ordemServicoService.alterarPrioridade(os.numero(), null).prioridade()).isNull(); // RN-OS-16: opcional

        assertThatThrownBy(() -> ordemServicoService.alterarPrioridade(os.numero(), 101)).hasMessage("Prioridade fora da escala.");
        assertThatThrownBy(() -> ordemServicoService.alterarPrioridade(os.numero(), -1)).hasMessage("Prioridade fora da escala.");
        assertThatThrownBy(() -> ordemServicoService.abrir(new AbrirOsRequest(c.codigo(), 101, List.of(itemJateamento("B", null)))))
                .hasMessage("Prioridade fora da escala.");
    }

    /** RN-ITE-17: as cinco unidades da Fênix são aceitas e gravadas em maiúsculas. */
    @Test
    void rnIte17_unidadesDaFenix() {
        ClienteResponse c = novoClientePF("Unidades " + System.nanoTime());
        for (String[] par : new String[][] {{"Peça", "PEÇA"}, {"conjunto", "CONJUNTO"}, {"kg", "KG"}, {"Metro", "METRO"}, {"JOGO", "JOGO"}}) {
            OsResponse os = abrirOs(c.codigo(), new ItemRequest("Item " + par[0], BigDecimal.ONE, par[0], null, null, null, null,
                    List.of(rota(RotaProcessoService.JATEAMENTO))));
            assertThat(os.itens().get(0).unidade()).isEqualTo(par[1]);
        }
        for (String antiga : new String[] {"UN", "PC", "M", "M2"}) {
            assertThatThrownBy(() -> abrirOs(c.codigo(), new ItemRequest("Item", BigDecimal.ONE, antiga, null, null, null, null,
                    List.of(rota(RotaProcessoService.JATEAMENTO)))))
                    .hasMessage("Unidade inválida.");
        }
    }
}
