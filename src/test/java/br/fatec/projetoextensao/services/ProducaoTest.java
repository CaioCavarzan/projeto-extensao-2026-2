package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.fatec.projetoextensao.dtos.CorrecaoEtapaRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaRequest;
import br.fatec.projetoextensao.dtos.CronogramaRequest;
import br.fatec.projetoextensao.dtos.CronogramaResponse;
import br.fatec.projetoextensao.dtos.EtapaResponse;
import br.fatec.projetoextensao.dtos.IndicadorRetrabalhoResponse;
import br.fatec.projetoextensao.dtos.IniciarEtapaRequest;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.enums.StatusOsEnum;

/** INCLUÍDO (20/10/2026): rota e execução das etapas (RN-IRP) e cronograma (RN-CRO e RN-CRI). */
class ProducaoTest extends TesteBase {

    private IniciarEtapaRequest operador() {
        return new IniciarEtapaRequest(admin.getFuncionarioCodigo());
    }

    /** OS com 1 item pronto para execução: Jateamento e Pintura, cor azul e tinta azul com estoque. */
    private OsResponse osComPintura() {
        int azul = novaCor("Azul");
        TintaResponse t = novaTinta(azul, new BigDecimal("50"));
        return abrirOs(novoClientePF("Cliente Produção " + System.nanoTime()).codigo(),
                itemPintura("Portão", azul, t.codigo(), new BigDecimal("300")));
    }

    /** RN-IRP-01: ordem 1, 2... calculada pelo Service; a etapa acrescentada recebe a maior ordem + 1. */
    @Test
    void rnIrp01_ordemCalculada() {
        OsResponse os = osComPintura();
        int item = os.itens().get(0).codigo();
        assertThat(os.itens().get(0).etapas()).extracting(EtapaResponse::ordem).containsExactly(1, 2);
        List<EtapaResponse> etapas = itemRotaProcessoService.adicionarEtapa(item, rota(RotaProcessoService.BANHO));
        assertThat(etapas).extracting(EtapaResponse::ordem).containsExactly(1, 2, 3);
    }

    /** RN-IRP-03 (operador), RN-IRP-05 (ordem) e RN-IRP-04 (concluir sem iniciar). */
    @Test
    void rnIrp03_rnIrp04_rnIrp05_execucao() {
        OsResponse os = osComPintura();
        List<EtapaResponse> etapas = os.itens().get(0).etapas();
        int jateamento = etapas.get(0).codigo();
        int pintura = etapas.get(1).codigo();
        assertThatThrownBy(() -> itemRotaProcessoService.iniciarEtapa(jateamento, new IniciarEtapaRequest(null)))
                .hasMessage("Informe o operador da etapa.");
        assertThatThrownBy(() -> itemRotaProcessoService.iniciarEtapa(pintura, operador()))
                .hasMessage("Conclua a etapa anterior antes.");
        assertThatThrownBy(() -> itemRotaProcessoService.concluirEtapa(jateamento))
                .hasMessage("A etapa precisa ter sido iniciada.");
        EtapaResponse iniciada = itemRotaProcessoService.iniciarEtapa(jateamento, operador());
        assertThat(iniciada.dataInicio()).isNotNull(); // RN-IRP-07: hora do servidor
        assertThat(ordemServicoService.statusAtual(os.numero())).isEqualTo(StatusOsEnum.EM_PRODUCAO.getNome()); // RN-OS-06
    }

    /** RN-IRP-06: concluída a última etapa, o item passa a 'Executada' (com data de finalização). */
    @Test
    void rnIrp06_ultimaEtapaExecutaItem() {
        OsResponse os = osComPintura();
        int item = os.itens().get(0).codigo();
        for (EtapaResponse e : os.itens().get(0).etapas()) {
            itemRotaProcessoService.iniciarEtapa(e.codigo(), operador());
            itemRotaProcessoService.concluirEtapa(e.codigo());
        }
        assertThat(itemService.buscar(item).status()).isEqualTo(StatusItemEnum.EXECUTADA.getNome());
        assertThat(itemService.buscar(item).dataFinalizacao()).isNotNull();
    }

    /**
     * RN-IRP-02 e RN-ITE-06: retrabalho = nova linha da mesma etapa, com nova ordem; a etapa concluída não reabre e o
     * status do item não muda (continua 'Executada'). A etapa de retrabalho pode ser executada.
     */
    @Test
    void rnIrp02_rnIte06_retrabalho() {
        OsResponse os = osComPintura();
        int item = os.itens().get(0).codigo();
        List<EtapaResponse> etapas = os.itens().get(0).etapas();
        for (EtapaResponse e : etapas) {
            itemRotaProcessoService.iniciarEtapa(e.codigo(), operador());
            itemRotaProcessoService.concluirEtapa(e.codigo());
        }
        int pintura = etapas.get(1).codigo();
        assertThatThrownBy(() -> itemRotaProcessoService.iniciarEtapa(pintura, operador()))
                .hasMessage("Etapa concluída não pode ser reaberta; registre retrabalho.");
        List<EtapaResponse> comRetrabalho = itemRotaProcessoService.registrarRetrabalho(pintura);
        EtapaResponse nova = comRetrabalho.get(2);
        assertThat(nova.ordem()).isEqualTo(3);
        assertThat(nova.rotaNome()).isEqualTo(RotaProcessoService.PINTURA);
        assertThat(nova.retrabalho()).isTrue();
        assertThat(itemService.buscar(item).status()).isEqualTo(StatusItemEnum.EXECUTADA.getNome());
        itemRotaProcessoService.iniciarEtapa(nova.codigo(), operador());
        itemRotaProcessoService.concluirEtapa(nova.codigo());
        assertThat(itemService.buscar(item).status()).isEqualTo(StatusItemEnum.EXECUTADA.getNome());
    }

    /** RN-IRP-09: 2 itens executados, 1 com etapa repetida -> 50%. */
    @Test
    void rnIrp09_percentualDeRetrabalho() {
        OsResponse os = abrirOs(novoClientePF("Cliente Indicador").codigo(),
                itemJateamento("Com retrabalho", null), itemJateamento("Sem retrabalho", null));
        for (int i = 0; i < 2; i++) {
            EtapaResponse e = os.itens().get(i).etapas().get(0);
            itemRotaProcessoService.iniciarEtapa(e.codigo(), operador());
            itemRotaProcessoService.concluirEtapa(e.codigo());
        }
        List<EtapaResponse> etapas = itemRotaProcessoService.registrarRetrabalho(os.itens().get(0).etapas().get(0).codigo());
        itemRotaProcessoService.iniciarEtapa(etapas.get(1).codigo(), operador());
        itemRotaProcessoService.concluirEtapa(etapas.get(1).codigo());
        IndicadorRetrabalhoResponse r = itemRotaProcessoService.percentualRetrabalho(LocalDate.now(), LocalDate.now());
        assertThat(r.itensExecutados()).isEqualTo(2);
        assertThat(r.itensComRetrabalho()).isEqualTo(1);
        assertThat(r.percentual()).isEqualByComparingTo("50.00");
    }

    /** RN-IRP-08 e RN-ITE-03: etapa iniciada não sai da rota; a última etapa também não. */
    @Test
    void rnIrp08_rnIte03_removerEtapa() {
        OsResponse os = osComPintura();
        List<EtapaResponse> etapas = os.itens().get(0).etapas();
        itemRotaProcessoService.iniciarEtapa(etapas.get(0).codigo(), operador());
        assertThatThrownBy(() -> itemRotaProcessoService.removerEtapa(etapas.get(0).codigo()))
                .hasMessage("Etapa já iniciada não pode ser removida.");
        OsResponse os2 = abrirOs(novoClientePF("Cliente Rota").codigo(), itemJateamento("Uma etapa", null));
        int unica = os2.itens().get(0).etapas().get(0).codigo();
        assertThatThrownBy(() -> itemRotaProcessoService.removerEtapa(unica))
                .hasMessage("Informe pelo menos uma etapa do processo.");
    }

    /** RN-ITE-05: tirar a Pintura da rota deixa o item sem cor e sem tinta. */
    @Test
    void rnIte05_semPinturaFicaSemCorETinta() {
        OsResponse os = osComPintura();
        int item = os.itens().get(0).codigo();
        int pintura = os.itens().get(0).etapas().get(1).codigo();
        itemRotaProcessoService.removerEtapa(pintura);
        assertThat(itemService.buscar(item).corCodigo()).isNull();
        assertThat(itemService.buscar(item).tintaCodigo()).isNull();
    }

    /** Pintura só começa com a tinta definida; OS pausada não inicia etapa. */
    @Test
    void pinturaSemTintaEOsPausada() {
        int azul = novaCor("Azul");
        OsResponse os = abrirOs(novoClientePF("Cliente Pausa").codigo(), itemJateamento("Pronto", null),
                itemPintura("Sem tinta", azul, null, null));
        EtapaResponse jat = os.itens().get(0).etapas().get(0);
        itemRotaProcessoService.iniciarEtapa(jat.codigo(), operador());
        assertThatThrownBy(() -> itemRotaProcessoService.iniciarEtapa(os.itens().get(1).etapas().get(0).codigo(), operador()))
                .hasMessage("Item ainda não está pronto para execução: confira a rota, a cor pedida e a tinta.");
        ordemServicoService.pausar(os.numero());
        assertThatThrownBy(() -> itemRotaProcessoService.concluirEtapa(jat.codigo()))
                .hasMessage("OS pausada: retome a OS antes de iniciar ou concluir etapas.");
    }

    /** RN-IRP-07: correção de datas só ajusta horários de etapa já iniciada; conclusão não antes do início. */
    @Test
    void rnIrp07_correcaoDeDatas() {
        OsResponse os = osComPintura();
        EtapaResponse e = os.itens().get(0).etapas().get(0);
        assertThatThrownBy(() -> itemRotaProcessoService.corrigirDatas(e.codigo(),
                new CorrecaoEtapaRequest(LocalDateTime.now().minusHours(1), null, null)))
                .hasMessage("Etapa ainda não iniciada: use a opção de iniciar.");
        itemRotaProcessoService.iniciarEtapa(e.codigo(), operador());
        itemRotaProcessoService.concluirEtapa(e.codigo());
        assertThatThrownBy(() -> itemRotaProcessoService.corrigirDatas(e.codigo(),
                new CorrecaoEtapaRequest(LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(2), null)))
                .hasMessage("A conclusão não pode ser antes do início.");
        EtapaResponse corrigida = itemRotaProcessoService.corrigirDatas(e.codigo(),
                new CorrecaoEtapaRequest(LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(2), null));
        assertThat(corrigida.dataConclusao()).isAfter(corrigida.dataInicio());
    }

    // ------------------------------------------------------------------ cronograma

    /** RN-CRO-01 (sem etapas), RN-CRO-06 (dia passado) e RN-CRO-02 (um por dia). RN-SIS-08: registrado pelo logado. */
    @Test
    void rnCro01_rnCro02_rnCro06_criacao() {
        OsResponse os = osComPintura();
        int etapa = os.itens().get(0).etapas().get(0).codigo();
        LocalDate dia = LocalDate.now().plusDays(30);
        assertThatThrownBy(() -> cronogramaService.criar(new CronogramaRequest(dia, null, List.of())))
                .hasMessage("Inclua pelo menos uma etapa no cronograma.");
        assertThatThrownBy(() -> cronogramaService.criar(new CronogramaRequest(LocalDate.now().minusDays(1), null,
                List.of(new CronogramaEtapaRequest(etapa, BigDecimal.TEN)))))
                .hasMessage("Não é possível planejar ou alterar um dia que já passou.");
        CronogramaResponse c = cronogramaService.criar(new CronogramaRequest(dia, "Turno da manhã",
                List.of(new CronogramaEtapaRequest(etapa, BigDecimal.TEN))));
        assertThat(c.registradoPor()).isEqualTo(LOGIN_ADMIN);
        assertThatThrownBy(() -> cronogramaService.criar(new CronogramaRequest(dia, null,
                List.of(new CronogramaEtapaRequest(etapa, BigDecimal.ONE)))))
                .hasMessage("Já existe cronograma para esta data.");
    }

    /** RN-CRI-03 (etapa repetida), RN-CRI-05 (quantidade acima da pendente) e RN-CRI-01 (etapa concluída). */
    @Test
    void rnCri01_rnCri03_rnCri05_etapasNoCronograma() {
        OsResponse os = osComPintura(); // item com 100 peças
        int jateamento = os.itens().get(0).etapas().get(0).codigo();
        int pintura = os.itens().get(0).etapas().get(1).codigo();
        LocalDate d1 = LocalDate.now().plusDays(40);
        cronogramaService.criar(new CronogramaRequest(d1, null, List.of(new CronogramaEtapaRequest(pintura, new BigDecimal("80")))));
        // Cada tentativa recusada usa outro dia: dentro da transação do teste, o cronograma da tentativa anterior
        // ainda existe até o fim do teste (no sistema de verdade, a recusa desfaz tudo: RN-SIS-04).
        LocalDate d2 = LocalDate.now().plusDays(41);
        assertThatThrownBy(() -> cronogramaService.criar(new CronogramaRequest(d2, null,
                List.of(new CronogramaEtapaRequest(pintura, new BigDecimal("30"))))))
                .hasMessage("Quantidade planejada maior que a pendente (20.00).");
        LocalDate d3 = LocalDate.now().plusDays(42);
        assertThatThrownBy(() -> cronogramaService.criar(new CronogramaRequest(d3, null, List.of(
                new CronogramaEtapaRequest(jateamento, BigDecimal.ONE), new CronogramaEtapaRequest(jateamento, BigDecimal.ONE)))))
                .hasMessage("Esta etapa já está no cronograma.");
        itemRotaProcessoService.iniciarEtapa(jateamento, operador());
        itemRotaProcessoService.concluirEtapa(jateamento);
        LocalDate d4 = LocalDate.now().plusDays(43);
        assertThatThrownBy(() -> cronogramaService.criar(new CronogramaRequest(d4, null,
                List.of(new CronogramaEtapaRequest(jateamento, BigDecimal.ONE)))))
                .hasMessage("Esta etapa já foi concluída ou o item está encerrado.");
    }

    /** RN-CRI-07: remover e reordenar renumeram a fila; RN-CRO-01: a última etapa não sai. */
    @Test
    void rnCri07_filaRenumerada() {
        OsResponse os = abrirOs(novoClientePF("Cliente Fila").codigo(), itemJateamento("A", null),
                itemJateamento("B", null), itemJateamento("C", null));
        int a = os.itens().get(0).etapas().get(0).codigo();
        int b = os.itens().get(1).etapas().get(0).codigo();
        int c = os.itens().get(2).etapas().get(0).codigo();
        CronogramaResponse cron = cronogramaService.criar(new CronogramaRequest(LocalDate.now().plusDays(50), null,
                List.of(new CronogramaEtapaRequest(a, BigDecimal.ONE), new CronogramaEtapaRequest(b, BigDecimal.ONE),
                        new CronogramaEtapaRequest(c, BigDecimal.ONE))));
        CronogramaResponse semB = cronogramaService.removerEtapa(cron.codigo(), b);
        assertThat(semB.fila()).extracting(f -> f.etapaCodigo() + "@" + f.posicao()).containsExactly(a + "@0", c + "@1");
        CronogramaResponse invertida = cronogramaService.reordenar(cron.codigo(), List.of(c, a));
        assertThat(invertida.fila()).extracting(f -> f.etapaCodigo() + "@" + f.posicao()).containsExactly(c + "@0", a + "@1");
        cronogramaService.removerEtapa(cron.codigo(), a);
        assertThatThrownBy(() -> cronogramaService.removerEtapa(cron.codigo(), c))
                .hasMessage("O cronograma precisa de pelo menos uma etapa; exclua o cronograma.");
    }

    /** RN-IRP-08 (complemento): etapa que está num cronograma precisa sair dele antes de sair da rota. */
    @Test
    void etapaNoCronogramaNaoSaiDaRota() {
        OsResponse os = osComPintura();
        int pintura = os.itens().get(0).etapas().get(1).codigo();
        cronogramaService.criar(new CronogramaRequest(LocalDate.now().plusDays(60), null,
                List.of(new CronogramaEtapaRequest(pintura, BigDecimal.ONE))));
        assertThatThrownBy(() -> itemRotaProcessoService.removerEtapa(pintura))
                .hasMessage("Etapa planejada em cronograma: retire-a do cronograma antes.");
    }

    /** RN-ITE-03: item sem etapa é recusado. */
    @Test
    void rnIte03_itemSemEtapa() {
        int cliente = novoClientePF("Sem Rota").codigo();
        assertThatThrownBy(() -> abrirOs(cliente, new ItemRequest("Sem rota", BigDecimal.ONE, "PEÇA", null, null, null,
                null, List.of())))
                .hasMessage("Informe pelo menos uma etapa do processo.");
    }
}
