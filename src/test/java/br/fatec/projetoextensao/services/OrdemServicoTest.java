package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.dtos.EtapaResponse;
import br.fatec.projetoextensao.dtos.HistoricoStatusResponse;
import br.fatec.projetoextensao.dtos.IniciarEtapaRequest;
import br.fatec.projetoextensao.dtos.ItemAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;

/** INCLUÍDO (20/10/2026): OS, itens e status (RN-OS, RN-HOS, RN-ITE, RN-HIT e aba "Fluxo de status"). */
class OrdemServicoTest extends TesteBase {

    private int cliente() {
        return novoClientePF("Cliente OS " + System.nanoTime()).codigo();
    }

    /** Executa todas as etapas do item, na ordem (RN-IRP-05), com o admin de teste como operador. */
    private void executarItem(int itemCodigo) {
        for (EtapaResponse e : itemRotaProcessoService.listarDoItem(itemCodigo)) {
            if (e.dataConclusao() == null) {
                itemRotaProcessoService.iniciarEtapa(e.codigo(), new IniciarEtapaRequest(admin.getFuncionarioCodigo()));
                itemRotaProcessoService.concluirEtapa(e.codigo());
            }
        }
    }

    /** RN-OS-01: OS sem itens é recusada. */
    @Test
    void rnOs01_osSemItens() {
        int c = cliente();
        assertThatThrownBy(() -> ordemServicoService.abrir(new AbrirOsRequest(c, null, List.of())))
                .hasMessage("Inclua pelo menos um item na OS.");
    }

    /**
     * RN-OS-02, RN-ITE-02 e aba Fluxo de status: a OS nasce 'Criada'; o item nasce 'Pendente' e, como só tem
     * Jateamento (não precisa de cor nem tinta), passa na hora a 'Aguardando execução'.
     * RN-SIS-08: a OS registra o usuário logado.
     */
    @Test
    void rnOs02_rnIte02_statusIniciais() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Grade", new BigDecimal("80")));
        assertThat(os.status()).isEqualTo(StatusOsEnum.CRIADA.getNome());
        assertThat(os.registradaPor()).isEqualTo(LOGIN_ADMIN);
        int item = os.itens().get(0).codigo();
        List<HistoricoStatusResponse> hist = itemService.historico(item);
        assertThat(hist).extracting(HistoricoStatusResponse::status)
                .containsExactly(StatusItemEnum.PENDENTE.getNome(), StatusItemEnum.AGUARDANDO_EXECUCAO.getNome());
        assertThat(ordemServicoService.historico(os.numero())).extracting(HistoricoStatusResponse::status)
                .containsExactly(StatusOsEnum.CRIADA.getNome());
    }

    /** RN-SIS-04: OS com um item inválido não grava nada (sem a transação do teste em volta). */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rnSis04_osComItemInvalidoNaoGravaNada() {
        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(pessoa("Cliente Atomicidade"), novoCpf(),
                null, null, null));
        long osAntes = ordemServicoRepository.count();
        long itensAntes = itemRepository.count();
        ItemRequest valido = itemJateamento("Válido", new BigDecimal("10"));
        ItemRequest invalido = new ItemRequest("Inválido", BigDecimal.ZERO, "PEÇA", null, null, null, null,
                List.of(rota(RotaProcessoService.JATEAMENTO)));
        assertThatThrownBy(() -> ordemServicoService.abrir(new AbrirOsRequest(c.codigo(), null, List.of(valido, invalido))))
                .hasMessage("Quantidade deve ser maior que zero.");
        assertThat(ordemServicoRepository.count()).isEqualTo(osAntes);
        assertThat(itemRepository.count()).isEqualTo(itensAntes);
    }

    /** RN-OS-14 e RN-OS-15: valor = soma dos itens; item sem valor deixa a OS incompleta; item cancelado sai da soma. */
    @Test
    void rnOs14_rnOs15_valorDaOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", new BigDecimal("100.00")),
                itemJateamento("B", new BigDecimal("50.50")), itemJateamento("C", null));
        assertThat(os.valorOs()).isEqualByComparingTo("150.50");
        assertThat(os.valorIncompleto()).isTrue();
        itemService.cancelar(os.itens().get(0).codigo()); // RN-ITE-08
        itemService.cancelar(os.itens().get(2).codigo());
        OsResponse depois = ordemServicoService.buscar(os.numero());
        assertThat(depois.valorOs()).isEqualByComparingTo("50.50");
        assertThat(depois.valorIncompleto()).isFalse();
    }

    /** RN-ITE-08: alterar o valor do item de 100 para 120 sobe o valor da OS em 20. */
    @Test
    void rnIte08_alterarItemRecalculaOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", new BigDecimal("100")));
        ItemResponse i = os.itens().get(0);
        itemService.atualizar(i.codigo(), new ItemAtualizacaoRequest("A", new BigDecimal("10"), "PEÇA", null, null,
                new BigDecimal("120")));
        assertThat(ordemServicoService.buscar(os.numero()).valorOs()).isEqualByComparingTo("120.00");
    }

    /** RN-ITE-05: item com Pintura exige cor; item sem Pintura não leva cor. */
    @Test
    void rnIte05_corEPintura() {
        int c = cliente();
        int cor = novaCor("Vermelho");
        assertThatThrownBy(() -> abrirOs(c, new ItemRequest("Sem cor", BigDecimal.ONE, "PEÇA", null, null, null, null,
                List.of(rota(RotaProcessoService.PINTURA)))))
                .hasMessage("Informe a cor: o item tem pintura.");
        assertThatThrownBy(() -> abrirOs(c, new ItemRequest("Com cor sem pintura", BigDecimal.ONE, "PEÇA", cor, null,
                null, null, List.of(rota(RotaProcessoService.JATEAMENTO)))))
                .hasMessage("Item sem pintura na rota não leva cor nem tinta.");
    }

    /** RN-ITE-04: a tinta tem a mesma cor pedida. RN-ITE-12: tinta sem estoque gera aviso. */
    @Test
    void rnIte04_rnIte12_tintaECor() {
        int c = cliente();
        int azul = novaCor("Azul");
        int preto = novaCor("Preto");
        TintaResponse tintaPreta = novaTinta(preto, null);
        assertThatThrownBy(() -> abrirOs(c, itemPintura("Item azul", azul, tintaPreta.codigo(), null)))
                .hasMessageStartingWith("A tinta escolhida é de outra cor (");
        TintaResponse tintaAzulSemEstoque = novaTinta(azul, null);
        OsResponse os = abrirOs(c, itemPintura("Item azul", azul, tintaAzulSemEstoque.codigo(), null));
        assertThat(os.avisos()).contains("Atenção: tinta sem estoque.");
    }

    /** RN-ITE-17 (unidade da lista, em maiúsculas) e RN-ITE-20 (prazo não antes da entrada). */
    @Test
    void rnIte17_rnIte20_unidadeEPrazo() {
        int c = cliente();
        OsResponse os = abrirOs(c, new ItemRequest("A", BigDecimal.ONE, "kg", null, null, null, null,
                List.of(rota(RotaProcessoService.JATEAMENTO))));
        assertThat(os.itens().get(0).unidade()).isEqualTo("KG");
        assertThatThrownBy(() -> abrirOs(c, new ItemRequest("B", BigDecimal.ONE, "caixa", null, null, null, null,
                List.of(rota(RotaProcessoService.JATEAMENTO)))))
                .hasMessage("Unidade inválida.");
        // 22/10/2026: a lista da Fênix é Peça, Conjunto, kg, Metro, Jogo; as unidades provisórias antigas (UN, PC, M2) saíram.
        assertThatThrownBy(() -> abrirOs(c, new ItemRequest("B2", BigDecimal.ONE, "UN", null, null, null, null,
                List.of(rota(RotaProcessoService.JATEAMENTO)))))
                .hasMessage("Unidade inválida.");
        assertThatThrownBy(() -> abrirOs(c, new ItemRequest("C", BigDecimal.ONE, "PEÇA", null, null,
                LocalDate.now().minusDays(1), null, List.of(rota(RotaProcessoService.JATEAMENTO)))))
                .hasMessage("O prazo de entrega não pode ser anterior à entrada.");
    }

    /** Aba Fluxo de status: item com Pintura e sem tinta fica 'Pendente'; com a tinta definida, 'Aguardando execução'. */
    @Test
    void fluxo_pendenteAteDefinirATinta() {
        int azul = novaCor("Azul");
        OsResponse os = abrirOs(cliente(), itemPintura("Portão", azul, null, null));
        ItemResponse item = os.itens().get(0);
        assertThat(item.status()).isEqualTo(StatusItemEnum.PENDENTE.getNome());
        TintaResponse t = novaTinta(azul, new BigDecimal("20"));
        ItemResponse comTinta = itemService.definirTinta(item.codigo(), t.codigo());
        assertThat(comTinta.status()).isEqualTo(StatusItemEnum.AGUARDANDO_EXECUCAO.getNome());
    }

    /** RN-HIT-02 e RN-HIT-04: transições do item. */
    @Test
    void rnHit02_rnHit04_transicoesDoItem() {
        int azul = novaCor("Azul");
        OsResponse os = abrirOs(cliente(), itemPintura("Pendente", azul, null, null), itemJateamento("Pronto", null));
        int pendente = os.itens().get(0).codigo();
        int pronto = os.itens().get(1).codigo();
        assertThatThrownBy(() -> itemService.pausar(pendente))
                .hasMessage("Mudança de status não permitida: de Pendente para Pausada.");
        itemService.pausar(pronto);
        assertThatThrownBy(() -> itemService.pausar(pronto)).hasMessage("O item já está com este status.");
        assertThatThrownBy(() -> itemService.retomar(pendente)).hasMessage("Só item pausado pode ser retomado.");
        assertThat(itemService.retomar(pronto).status()).isEqualTo(StatusItemEnum.AGUARDANDO_EXECUCAO.getNome());
    }

    /** RN-ITE-07: item executado não é alterado; RN-ITE-28: entrega só com nr_ose. RN-ITE-26: data de finalização. */
    @Test
    void rnIte07_rnIte26_rnIte28_itemExecutado() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Peça", new BigDecimal("10")));
        int item = os.itens().get(0).codigo();
        assertThat(itemService.buscar(item).dataFinalizacao()).isNull();
        executarItem(item);
        ItemResponse executado = itemService.buscar(item);
        assertThat(executado.status()).isEqualTo(StatusItemEnum.EXECUTADA.getNome());
        assertThat(executado.dataFinalizacao()).isNotNull();
        assertThatThrownBy(() -> itemService.atualizar(item, new ItemAtualizacaoRequest("Peça", new BigDecimal("20"),
                "PEÇA", null, null, null)))
                .hasMessage("Item encerrado não pode ser alterado.");
        assertThatThrownBy(() -> itemService.registrarEntrega(item, " ")).hasMessage("Informe o número da OSE assinada.");
        assertThat(itemService.registrarEntrega(item, "OSE-123").entregue()).isTrue();
    }

    /**
     * RN-OS-04, RN-HOS-02 e RN-OS-03: a OS só conclui com todos os itens encerrados; concluída não reabre.
     * RN-OS-07: OS concluída não recebe item, mas aceita registrar a entrega (nr_ose).
     */
    @Test
    void rnOs04_rnOs03_rnOs07_conclusao() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null), itemJateamento("B", null));
        int a = os.itens().get(0).codigo();
        int b = os.itens().get(1).codigo();
        executarItem(a);
        assertThat(ordemServicoService.statusAtual(os.numero())).isEqualTo(StatusOsEnum.EM_PRODUCAO.getNome()); // RN-OS-06
        assertThatThrownBy(() -> ordemServicoService.concluir(os.numero())).hasMessage("Há itens não executados nesta OS.");
        executarItem(b);
        assertThat(ordemServicoService.concluir(os.numero()).status()).isEqualTo(StatusOsEnum.CONCLUIDA.getNome());
        assertThatThrownBy(() -> ordemServicoService.pausar(os.numero()))
                .hasMessage("OS concluída não pode ser reaberta; abra uma nova OS.");
        assertThatThrownBy(() -> itemService.incluir(os.numero(), itemJateamento("C", null)))
                .hasMessage("OS encerrada: não aceita alterações.");
        assertThat(itemService.registrarEntrega(a, "OSE-9").nrOse()).isEqualTo("OSE-9");
    }

    /** RN-HOS-02 e RN-HOS-04: Em produção ⇄ Pausada; repetir o status atual é recusado. */
    @Test
    void rnHos02_rnHos04_pausarERetomarOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null), itemJateamento("B", null));
        assertThatThrownBy(() -> ordemServicoService.pausar(os.numero()))
                .hasMessage("Mudança de status não permitida: de Criada para Pausada.");
        executarItem(os.itens().get(0).codigo());
        ordemServicoService.pausar(os.numero());
        assertThatThrownBy(() -> ordemServicoService.pausar(os.numero())).hasMessage("A OS já está com este status.");
        assertThat(ordemServicoService.retomar(os.numero()).status()).isEqualTo(StatusOsEnum.EM_PRODUCAO.getNome());
        assertThat(ordemServicoService.historico(os.numero())).extracting(HistoricoStatusResponse::status)
                .containsExactly("Criada", "Em produção", "Pausada", "Em produção");
    }

    /** RN-OS-05: cancelar a OS cancela os itens não executados; os executados ficam como estão. */
    @Test
    void rnOs05_cancelamento() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Executado", new BigDecimal("10")),
                itemJateamento("Pendente", new BigDecimal("20")));
        int executado = os.itens().get(0).codigo();
        executarItem(executado);
        OsResponse cancelada = ordemServicoService.cancelar(os.numero());
        assertThat(cancelada.status()).isEqualTo(StatusOsEnum.CANCELADA.getNome());
        assertThat(cancelada.itens()).extracting(ItemResponse::status)
                .containsExactly(StatusItemEnum.EXECUTADA.getNome(), StatusItemEnum.CANCELADA.getNome());
        assertThat(cancelada.valorOs()).isEqualByComparingTo("10.00");
    }

    /** RN-ITE-22: item com prazo vencido e não encerrado aparece na lista de atrasados. */
    @Test
    void rnIte22_itensAtrasados() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Atrasado", null));
        ItemEntity item = itemRepository.findById(os.itens().get(0).codigo()).orElseThrow();
        item.setDataEntrada(LocalDateTime.now().minusDays(10)); // simula item que entrou há 10 dias
        item.setPrazoEntrega(LocalDate.now().minusDays(1));
        itemRepository.save(item);
        assertThat(itemService.listarAtrasados()).extracting(ItemResponse::codigo).contains(item.getCodigo());
        assertThat(itemService.buscar(item.getCodigo()).atrasado()).isTrue();
    }

    /** RN-ITE-01: a OS do item não muda (o DTO de alteração não tem OS) e o score nunca vem do front (RN-ITE-29). */
    @Test
    void rnIte01_rnIte29_osEScoreNaoVemDoFront() {
        assertThat(java.util.Arrays.stream(ItemAtualizacaoRequest.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("osNumero", "osCodigo", "scorePrioridade");
        OsResponse os = abrirOs(cliente(), itemJateamento("Score", null));
        assertThat(os.itens().get(0).scorePrioridade()).isNull();
    }

    /** RN-HIT-03: o status atual é o registro mais recente, mesmo com mudanças no mesmo segundo. */
    @Test
    void rnHit03_statusAtualComMudancasRapidas() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Rápido", null));
        int item = os.itens().get(0).codigo();
        itemService.pausar(item);
        itemService.retomar(item);
        itemService.pausar(item);
        assertThat(itemService.buscar(item).status()).isEqualTo(StatusItemEnum.PAUSADA.getNome());
        assertThat(itemService.historico(item)).hasSize(5);
    }

    /** RN-SIS-05 e RN-OS-13: o valor da OS não é aceito do front (o DTO de abertura não tem valor nem usuário). */
    @Test
    void rnSis05_dtoDeAberturaSemCamposDoSistema() {
        assertThat(java.util.Arrays.stream(AbrirOsRequest.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("clienteCodigo", "prioridade", "itens");
    }

    /** RN-OS-07: item de OS cancelada não é alterado. */
    @Test
    void rnOs07_osCanceladaNaoAceitaAlteracao() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null));
        ordemServicoService.cancelar(os.numero());
        int item = os.itens().get(0).codigo();
        assertThatThrownBy(() -> itemService.atualizar(item, new ItemAtualizacaoRequest("A", BigDecimal.ONE, "PEÇA",
                null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("OS encerrada: não aceita alterações.");
    }
}
