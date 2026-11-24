package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import br.fatec.projetoextensao.dtos.CronogramaEtapaRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaResponse;
import br.fatec.projetoextensao.dtos.CronogramaRequest;
import br.fatec.projetoextensao.dtos.CronogramaResponse;
import br.fatec.projetoextensao.dtos.EtapaResponse;
import br.fatec.projetoextensao.dtos.EventoAuditoriaResponse;
import br.fatec.projetoextensao.dtos.GraficoResponse;
import br.fatec.projetoextensao.dtos.IndicadorRetrabalhoResponse;
import br.fatec.projetoextensao.dtos.IniciarEtapaRequest;
import br.fatec.projetoextensao.dtos.ItemAtrasadoResponse;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.PainelOsResponse;
import br.fatec.projetoextensao.dtos.ProdutividadeResponse;
import br.fatec.projetoextensao.dtos.ReplanejamentoRequest;
import br.fatec.projetoextensao.dtos.ReplanejamentoResponse;
import br.fatec.projetoextensao.dtos.RestauracaoRequest;
import br.fatec.projetoextensao.dtos.RetrabalhoMensalResponse;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.enums.ObjetoBancoEnum;
import br.fatec.projetoextensao.enums.ObjetoBancoEnum.Scripts;
import br.fatec.projetoextensao.exceptions.ErrosBanco;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ProcedimentosBanco;
import jakarta.persistence.EntityManager;

/**
 * INCLUÍDO (18/11/2026) [E3-53]: a integração do Java com os objetos da Etapa 3 (functions, views, procedures e
 * triggers), testada de ponta a ponta no H2 dos testes, onde os objetos são imitados por etapa3-h2.sql, RotinasH2,
 * TriggersH2 e ProcedimentosBancoH2 com as mesmas regras e os mesmos códigos de erro dos scripts.
 *
 * Os testes que mexem direto no banco (JdbcTemplate) mostram o que mudou: a regra agora é cumprida pelo banco mesmo
 * quando o dado é alterado por fora do sistema. Datas de cronograma são sorteadas bem no futuro para não colidir
 * com cronogramas gravados por outros testes (RN-CRO-02: um por dia).
 */
class Etapa3IntegracaoTest extends TesteBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ProcedimentosBanco procedimentos;
    @Autowired private IndicadorService indicadorService;
    @Autowired private DashboardService dashboardService;
    @Autowired private BancoDadosService bancoDadosService;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private int cliente() {
        return novoClientePF("Cliente E3 " + System.nanoTime()).codigo();
    }

    private IniciarEtapaRequest operador() {
        return new IniciarEtapaRequest(admin.getFuncionarioCodigo());
    }

    /** Executa todas as etapas do item, na ordem, com o admin de teste como operador. */
    private void executarItem(int itemCodigo) {
        for (EtapaResponse e : itemRotaProcessoService.listarDoItem(itemCodigo)) {
            if (e.dataConclusao() == null) {
                itemRotaProcessoService.iniciarEtapa(e.codigo(), operador());
                itemRotaProcessoService.concluirEtapa(e.codigo());
            }
        }
    }

    private int primeiraEtapa(int itemCodigo) {
        return itemRotaProcessoService.listarDoItem(itemCodigo).get(0).codigo();
    }

    /** valor_os como está gravado na tabela (depois de mandar ao banco o que o Hibernate segura). */
    private BigDecimal valorNoBanco(int nrOs) {
        entityManager.flush();
        return jdbc.queryForObject("select valor_os from OrdensDeServico where nr_os = ?", BigDecimal.class, nrOs);
    }

    private static LocalDate diaFuturo() {
        return LocalDate.now().plusDays(ThreadLocalRandom.current().nextInt(400, 4000));
    }

    // ------------------------------------------------------------------ triggers

    /** trg_itens_sincronizar_valor_os: o valor da OS é somado pelo banco, inclusive quando o item muda por fora. */
    @Test
    void trigger_valorDaOsCalculadoPeloBanco() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", new BigDecimal("100.00")),
                itemJateamento("B", new BigDecimal("50.50")));
        assertThat(os.valorOs()).isEqualByComparingTo("150.50"); // relido do banco por ValorOsService.sincronizar
        assertThat(valorNoBanco(os.numero())).isEqualByComparingTo("150.50");

        jdbc.update("update Itens set valor_total_item = 70 where codigo = ?", os.itens().get(1).codigo());
        assertThat(valorNoBanco(os.numero())).isEqualByComparingTo("170.00");
        assertThat(jdbc.queryForObject("select dbo.fn_valor_os(?)", BigDecimal.class, os.numero()))
                .isEqualByComparingTo("170.00");
    }

    /** E3-07: com valor_os "só leitura" no JPA, o UPDATE da OS pelo Hibernate não desfaz o valor do trigger. */
    @Test
    void entidade_naoRegravaValorDaOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", new BigDecimal("100")));
        jdbc.update("update Itens set valor_total_item = 300 where codigo = ?", os.itens().get(0).codigo());
        ordemServicoService.alterarPrioridade(os.numero(), 77); // UPDATE de OrdensDeServico feito pelo Hibernate
        assertThat(valorNoBanco(os.numero())).isEqualByComparingTo("300.00");
    }

    /** trg_historicos_status_itens_sincronizar_item_e_os: data_finalizacao vem do histórico 'Executada'. */
    @Test
    void trigger_dataDeFinalizacaoPreenchidaPeloBanco() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null));
        int item = os.itens().get(0).codigo();
        executarItem(item);
        ItemResponse r = itemService.buscar(item);
        assertThat(r.status()).isEqualTo("Executada");
        Timestamp executada = jdbc.queryForObject("""
                select max(h.data_hora) from HistoricosStatusItens h
                inner join StatusItens s on s.codigo = h.status_item_codigo
                where h.item_codigo = ? and s.status = 'Executada'
                """, Timestamp.class, item);
        assertThat(r.dataFinalizacao()).isEqualTo(executada.toLocalDateTime());

        itemService.registrarEntrega(item, "OSE-123"); // UPDATE do item depois: a data do trigger continua lá
        entityManager.flush();
        assertThat(jdbc.queryForObject("select data_finalizacao from Itens where codigo = ?", Timestamp.class, item))
                .isEqualTo(executada);
    }

    /** trg_itens_sincronizar_valor_os: tinta de outra cor gravada por fora é recusada com o THROW 50110. */
    @Test
    void trigger_corDaTintaDiferente50110() {
        int azul = novaCor("Azul");
        int vermelho = novaCor("Vermelho");
        TintaResponse vermelha = novaTinta(vermelho, BigDecimal.TEN);
        OsResponse os = abrirOs(cliente(), itemPintura("Peça azul", azul, null, null));
        int item = os.itens().get(0).codigo();
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update("update Itens set tinta_codigo = ? where codigo = ?", vermelha.codigo(), item))
                .satisfies(e -> {
                    assertThat(ErrosBanco.temCodigo(e, ErrosBanco.COR_DO_ITEM_DIVERGE_DA_TINTA)).isTrue();
                    assertThat(ErrosBanco.traduzir(e)).isInstanceOf(RegraNegocioException.class)
                            .hasMessage("Inconsistência no item: a cor informada diverge da cor cadastrada na tinta.");
                });
        // pelo sistema, a RN-ITE-04 recusa antes de chegar ao banco, com a mensagem de sempre
        assertThatThrownBy(() -> itemService.definirTinta(item, vermelha.codigo()))
                .hasMessageStartingWith("A tinta escolhida é de outra cor");
    }

    /** Os dois históricos só recebem inclusões: UPDATE e DELETE recusados (THROW 50101 e 50120). */
    @Test
    void trigger_historicosImutaveis() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null));
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update(
                "update HistoricosStatusOs set data_hora = dateadd(second, 1, data_hora) where os_codigo = ?", os.numero()))
                .satisfies(e -> assertThat(ErrosBanco.traduzir(e)).isInstanceOf(RegraNegocioException.class)
                        .hasMessage("O histórico de status da OS é imutável: não são permitidos UPDATE ou DELETE."));
        assertThatThrownBy(() -> jdbc.update("delete from HistoricosStatusItens where item_codigo = ?", os.itens().get(0).codigo()))
                .satisfies(e -> assertThat(((RegraNegocioException) ErrosBanco.traduzir(e)).getTipo())
                        .isEqualTo(RegraNegocioException.Tipo.CONFLITO));
    }

    /** trg_historicos_status_os_proteger: OS cancelada não recebe novo status nem por fora do sistema (50102). */
    @Test
    void trigger_osEncerradaNaoRecebeStatus() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null));
        ordemServicoService.cancelar(os.numero());
        entityManager.flush();
        Integer criada = jdbc.queryForObject("select codigo from StatusOs where status = 'Criada'", Integer.class);
        assertThatThrownBy(() -> jdbc.update("insert into HistoricosStatusOs (os_codigo, status_codigo, data_hora) values (?, ?, ?)",
                os.numero(), criada, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5))))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.OS_ENCERRADA_NAO_MUDA_STATUS)).isTrue());
    }

    // ------------------------------------------------------------------ procedures

    /** sp_ajustar_estoque_tinta: o saldo é gravado pela procedure; ela mesma recusa 0, tinta inexistente e saldo negativo. */
    @Test
    void procedure_ajusteDeEstoque() {
        TintaResponse t = novaTinta(novaCor("Verde"), new BigDecimal("12.50")); // novaTinta chama ajustarEstoque
        assertThat(t.estoqueAtual()).isEqualByComparingTo("12.50");
        assertThat(jdbc.queryForObject("select estoque_atual from Tintas where codigo = ?", BigDecimal.class, t.codigo()))
                .isEqualByComparingTo("12.50");

        assertThatThrownBy(() -> procedimentos.ajustarEstoqueTinta(t.codigo(), new BigDecimal("-20")))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(ErrosBanco.converter((RuntimeException) e))
                        .hasMessage("Estoque insuficiente para realizar a baixa."));
        assertThatThrownBy(() -> procedimentos.ajustarEstoqueTinta(t.codigo(), BigDecimal.ZERO))
                .satisfies(e -> assertThat(ErrosBanco.converter((RuntimeException) e))
                        .hasMessage("Informe uma quantidade diferente de zero para ajustar o estoque."));
        assertThatThrownBy(() -> procedimentos.ajustarEstoqueTinta(999_999, BigDecimal.ONE))
                .satisfies(e -> assertThat(ErrosBanco.converter((RuntimeException) e))
                        .isInstanceOf(RecursoNaoEncontradoException.class).hasMessage("Tinta não encontrada."));

        // E3-07: alterar o cadastro da tinta não regrava o estoque (ex.: ajuste feito ao mesmo tempo por outro usuário)
        jdbc.update("update Tintas set estoque_atual = 40 where codigo = ?", t.codigo());
        tintaService.atualizar(t.codigo(), new TintaRequest(t.fornecedorCodigo(), t.corCodigo(), "Tinta renomeada",
                null, null, null, new BigDecimal("45.90")));
        entityManager.flush();
        assertThat(jdbc.queryForObject("select estoque_atual from Tintas where codigo = ?", BigDecimal.class, t.codigo()))
                .isEqualByComparingTo("40.00");
    }

    /**
     * sp_cancelar_ordem_servico: itens abertos cancelados em lote, executados mantidos, etapas pendentes tiradas dos
     * cronogramas futuros, valor recalculado; o Java avisa o cronograma que ficou vazio.
     */
    @Test
    void procedure_cancelamentoDaOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Executado", new BigDecimal("10")),
                itemJateamento("Pendente", new BigDecimal("20")), itemJateamento("Outro", new BigDecimal("5")));
        executarItem(os.itens().get(0).codigo());
        int etapaPendente = primeiraEtapa(os.itens().get(1).codigo());
        int etapaOutro = primeiraEtapa(os.itens().get(2).codigo());
        OsResponse outraOs = abrirOs(cliente(), itemJateamento("Outra OS", null));
        int etapaOutraOs = primeiraEtapa(outraOs.itens().get(0).codigo());
        LocalDate dia1 = diaFuturo();
        LocalDate dia2 = dia1.plusDays(1);
        CronogramaResponse c1 = cronogramaService.criar(new CronogramaRequest(dia1, null,
                List.of(new CronogramaEtapaRequest(etapaPendente, new BigDecimal("10")))));
        CronogramaResponse c2 = cronogramaService.criar(new CronogramaRequest(dia2, null,
                List.of(new CronogramaEtapaRequest(etapaOutro, BigDecimal.ONE), new CronogramaEtapaRequest(etapaOutraOs, BigDecimal.ONE))));

        OsResponse cancelada = ordemServicoService.cancelar(os.numero());

        assertThat(cancelada.status()).isEqualTo("Cancelada");
        assertThat(cancelada.itens()).extracting(ItemResponse::status).containsExactly("Executada", "Cancelada", "Cancelada");
        assertThat(cancelada.valorOs()).isEqualByComparingTo("10.00");
        assertThat(cronogramaService.buscar(c1.codigo()).fila()).isEmpty();
        assertThat(cronogramaService.buscar(c2.codigo()).fila()).extracting(CronogramaEtapaResponse::etapaCodigo)
                .containsExactly(etapaOutraOs);
        assertThat(cancelada.avisos()).contains(
                "2 item(ns) não executado(s) cancelado(s) junto com a OS (RN-OS-05).",
                "2 etapa(s) desta OS saíram do(s) cronograma(s) de " + dia1.format(DATA_BR) + ", " + dia2.format(DATA_BR) + ".",
                "O cronograma de " + dia1.format(DATA_BR) + " ficou sem etapas: inclua outras etapas ou exclua o cronograma (RN-CRO-01).");
        assertThat(cancelada.avisos()).noneMatch(a -> a.contains(dia2.format(DATA_BR) + " ficou sem etapas"));
        // a regra de status é conferida em Java antes da procedure (mesma mensagem de antes da Etapa 3)
        assertThatThrownBy(() -> ordemServicoService.cancelar(os.numero())).hasMessage("A OS já está com este status.");
    }

    /** sp_replanejar_cronograma: copia só as etapas pendentes; não duplica; não cria cronograma vazio. */
    @Test
    void procedure_replanejamento() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null), itemJateamento("B", null));
        int itemA = os.itens().get(0).codigo();
        int etapaA = primeiraEtapa(itemA);
        int etapaB = primeiraEtapa(os.itens().get(1).codigo());
        LocalDate origem = diaFuturo();
        LocalDate destino = origem.plusDays(1);
        cronogramaService.criar(new CronogramaRequest(origem, "Dia cheio",
                List.of(new CronogramaEtapaRequest(etapaA, BigDecimal.TEN), new CronogramaEtapaRequest(etapaB, BigDecimal.TEN))));
        executarItem(itemA); // a etapa A foi concluída: não deve ir para o destino

        ReplanejamentoResponse r = cronogramaService.replanejar(new ReplanejamentoRequest(origem, destino));
        assertThat(r.etapasMovidas()).isEqualTo(1);
        assertThat(r.cronogramaDestino().fila()).extracting(CronogramaEtapaResponse::etapaCodigo).containsExactly(etapaB);
        assertThat(r.cronogramaDestino().fila().get(0).posicao()).isZero();
        assertThat(r.cronogramaDestino().observacao()).isEqualTo("Replanejado a partir de " + origem);
        assertThat(r.cronogramaDestino().registradoPor()).isEqualTo(LOGIN_ADMIN);
        assertThat(r.avisos()).contains("O cronograma de " + destino.format(DATA_BR) + " foi criado pelo replanejamento.");
        assertThat(cronogramaService.buscarDoDia(origem).fila()).hasSize(2); // a origem fica como registro

        ReplanejamentoResponse deNovo = cronogramaService.replanejar(new ReplanejamentoRequest(origem, destino));
        assertThat(deNovo.etapasMovidas()).isZero();
        assertThat(deNovo.avisos().get(0)).startsWith("Nenhuma etapa pendente");
        assertThat(cronogramaService.buscarDoDia(destino).fila()).hasSize(1);

        LocalDate semCronograma = destino.plusDays(30);
        ReplanejamentoResponse vazio = cronogramaService.replanejar(new ReplanejamentoRequest(semCronograma, semCronograma.plusDays(1)));
        assertThat(vazio.etapasMovidas()).isZero();
        assertThat(vazio.cronogramaDestino()).isNull();
        assertThat(cronogramaRepository.existsByDataPlanejada(semCronograma.plusDays(1))).isFalse();

        assertThatThrownBy(() -> cronogramaService.replanejar(new ReplanejamentoRequest(destino, origem)))
                .hasMessage("A data de destino deve ser posterior à data de origem.");
        assertThatThrownBy(() -> cronogramaService.replanejar(new ReplanejamentoRequest(LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(1)))).hasMessage("Não é possível planejar ou alterar um dia que já passou.");
        assertThatThrownBy(() -> cronogramaService.replanejar(new ReplanejamentoRequest(null, destino)))
                .hasMessage("Informe o dia de origem e o dia de destino.");
        // a procedure também recusa por conta própria (THROW 50020 e 50021)
        assertThatThrownBy(() -> procedimentos.replanejarCronograma(destino, origem, admin.getFuncionarioCodigo()))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.REPLANEJAMENTO_DATAS_INVALIDAS)).isTrue());
        assertThatThrownBy(() -> procedimentos.replanejarCronograma(origem, destino, 999_999))
                .satisfies(e -> assertThat(ErrosBanco.converter((RuntimeException) e)).hasMessage("Usuário inválido ou inativo."));
    }

    // ------------------------------------------------------------------ functions

    /** fn_itens_atrasados: dias de atraso, próxima etapa pendente e operador dela. */
    @Test
    void funcao_itensAtrasados() {
        OsResponse os = abrirOs(cliente(), new ItemRequest("Peça atrasada", BigDecimal.TEN, "PEÇA", null, null, null, null,
                List.of(rota(RotaProcessoService.JATEAMENTO), rota(RotaProcessoService.BANHO))));
        int codigo = os.itens().get(0).codigo();
        ItemEntity item = itemRepository.findById(codigo).orElseThrow();
        item.setDataEntrada(LocalDateTime.now().minusDays(10));
        item.setPrazoEntrega(LocalDate.now().minusDays(3));
        itemRepository.save(item);
        List<EtapaResponse> etapas = itemRotaProcessoService.listarDoItem(codigo);
        itemRotaProcessoService.iniciarEtapa(etapas.get(0).codigo(), operador());
        itemRotaProcessoService.concluirEtapa(etapas.get(0).codigo());

        ItemAtrasadoResponse a = itemService.listarAtrasados().stream().filter(x -> x.codigo() == codigo).findFirst()
                .orElseThrow();
        assertThat(a.diasAtraso()).isEqualTo(3);
        assertThat(a.osNumero()).isEqualTo(os.numero());
        assertThat(a.clienteNome()).startsWith("Cliente E3");
        assertThat(a.etapaPendente()).isEqualTo(RotaProcessoService.BANHO);
        assertThat(a.operadorEtapa()).isNull(); // a etapa Banho ainda não foi iniciada
        assertThat(a.status()).isEqualTo("Aguardando execução");
    }

    /** fn_auditoria_timeline_os: eventos da OS, dos itens e das etapas, em ordem, com o papel do responsável. */
    @Test
    void funcao_auditoriaDaOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", null));
        executarItem(os.itens().get(0).codigo());
        List<EventoAuditoriaResponse> eventos = ordemServicoService.auditoria(os.numero());

        assertThat(eventos).extracting(EventoAuditoriaResponse::sequencia).isSorted().doesNotHaveDuplicates();
        assertThat(eventos.get(0).evento()).isEqualTo("Status da OS: Criada");
        assertThat(eventos.get(0).responsavel()).isEqualTo(LOGIN_ADMIN);
        assertThat(eventos.get(0).papelResponsavel()).isEqualTo("abriu a OS");
        assertThat(eventos).extracting(EventoAuditoriaResponse::evento).contains("Status do Item: Pendente",
                "Status do Item: Aguardando execução", "Início de Jateamento", "Status da OS: Em produção",
                "Status do Item: Executada");
        EventoAuditoriaResponse conclusao = eventos.stream().filter(e -> e.evento().startsWith("Conclusão de Jateamento"))
                .findFirst().orElseThrow();
        assertThat(conclusao.evento()).matches("Conclusão de Jateamento \\(\\d+ min\\)");
        assertThat(conclusao.categoria()).isEqualTo("Etapa de Produção");
        assertThat(conclusao.responsavel()).isEqualTo("Administrador de Teste");
        assertThat(conclusao.papelResponsavel()).isEqualTo("operador da etapa");
        assertThatThrownBy(() -> ordemServicoService.auditoria(999_999)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------------ views

    /** vw_painel_ordens_servico: contagens dos itens, valor incompleto e dados do cliente numa linha só. */
    @Test
    void view_painelDasOs() {
        OsResponse os = abrirOs(cliente(), itemJateamento("A", new BigDecimal("10")), itemJateamento("B", null),
                itemJateamento("C", new BigDecimal("5")));
        itemService.cancelar(os.itens().get(2).codigo());
        executarItem(os.itens().get(0).codigo());

        PainelOsResponse p = ordemServicoService.painel().stream().filter(x -> x.numero() == os.numero()).findFirst()
                .orElseThrow();
        assertThat(p.qtdItens()).isEqualTo(3);
        assertThat(p.qtdItensExecutados()).isEqualTo(1);
        assertThat(p.qtdItensCancelados()).isEqualTo(1);
        assertThat(p.qtdItensEmAberto()).isEqualTo(1);
        assertThat(p.qtdItensAtrasados()).isZero();
        assertThat(p.valorIncompleto()).isTrue(); // B não tem valor e não foi cancelado (RN-OS-15)
        assertThat(p.valorOs()).isEqualByComparingTo("10.00");
        assertThat(p.status()).isEqualTo("Em produção");
        assertThat(p.tipoCliente()).isEqualTo("PF");
        assertThat(p.documentoCliente()).hasSize(11);
        assertThat(p.registradaPor()).isEqualTo(LOGIN_ADMIN);
        // a tela da OS usa a mesma view para o "valor incompleto"
        assertThat(ordemServicoService.buscar(os.numero()).valorIncompleto()).isTrue();
    }

    /** vw_produtividade_funcionario e vw_retrabalho_mensal batem com o cálculo em Java do mesmo período. */
    @Test
    void views_produtividadeERetrabalhoMensal() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Com retrabalho", new BigDecimal("80")),
                itemJateamento("Sem retrabalho", new BigDecimal("20")));
        executarItem(os.itens().get(0).codigo());
        executarItem(os.itens().get(1).codigo());
        List<EtapaResponse> etapas = itemRotaProcessoService.registrarRetrabalho(primeiraEtapa(os.itens().get(0).codigo()));
        itemRotaProcessoService.iniciarEtapa(etapas.get(1).codigo(), operador());
        itemRotaProcessoService.concluirEtapa(etapas.get(1).codigo());
        LocalDate hoje = LocalDate.now();

        ProdutividadeResponse minha = indicadorService.produtividade(hoje, hoje).stream()
                .filter(p -> p.funcionarioCodigo() == admin.getFuncionarioCodigo() && p.processo().equals("Jateamento"))
                .findFirst().orElseThrow();
        assertThat(minha.etapasConcluidas()).isGreaterThanOrEqualTo(3);
        assertThat(minha.etapasRetrabalho()).isGreaterThanOrEqualTo(1);
        assertThat(minha.funcionarioNome()).isEqualTo("Administrador de Teste");

        RetrabalhoMensalResponse mes = indicadorService.retrabalhoMensal(1).stream()
                .filter(r -> r.mesReferencia().equals(hoje.withDayOfMonth(1))).findFirst().orElseThrow();
        IndicadorRetrabalhoResponse java = itemRotaProcessoService.percentualRetrabalho(hoje.withDayOfMonth(1), hoje);
        assertThat(mes.itensExecutados()).isEqualTo(java.itensExecutados());
        assertThat(mes.itensComRetrabalho()).isEqualTo(java.itensComRetrabalho());
        assertThat(mes.percentual()).isEqualByComparingTo(java.percentual());
        // o indicador do topo do dashboard é a mesma linha da view
        assertThat(dashboardService.resumo().retrabalhoMes().percentual()).isEqualByComparingTo(mes.percentual());

        assertThatThrownBy(() -> indicadorService.produtividade(hoje, hoje.minusDays(1)))
                .hasMessage("Informe o período: data inicial e data final.");
        assertThatThrownBy(() -> indicadorService.retrabalhoMensal(0)).hasMessage("Informe de 1 a 36 meses.");
    }

    /** Os 4 gráficos do dashboard passam a ter dados (antes: "Em desenvolvimento"). */
    @Test
    void dashboard_graficosComDadosDasViews() {
        OsResponse os = abrirOs(cliente(), itemJateamento("Gráfico", null));
        executarItem(os.itens().get(0).codigo());
        String hoje = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"));

        GraficoResponse producao = dashboardService.grafico("producao-diaria");
        assertThat(producao.disponivel()).isTrue();
        assertThat(producao.rotulos()).hasSize(DashboardService.DIAS_PRODUCAO).last().isEqualTo(hoje);
        assertThat(producao.series().get(0).valores().get(DashboardService.DIAS_PRODUCAO - 1)).isGreaterThanOrEqualTo(BigDecimal.ONE);

        assertThat(dashboardService.grafico("etapas-por-funcionario").rotulos()).contains("Administrador de Teste");
        assertThat(dashboardService.grafico("os-por-status").rotulos()).contains("Em produção");
        GraficoResponse retrabalho = dashboardService.grafico("retrabalho-mensal");
        String[] meses = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
        assertThat(retrabalho.rotulos()).hasSize(DashboardService.MESES_RETRABALHO)
                .last().isEqualTo(meses[LocalDate.now().getMonthValue() - 1] + "/" + String.format("%02d", LocalDate.now().getYear() % 100));
        assertThat(dashboardService.resumo().etapasConcluidasHoje()).isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------ backup, restore e scripts

    /** No H2 (testes) não há sys.objects, msdb nem BACKUP: a aba responde com mensagem clara. */
    @Test
    void bancoDeDados_soNoSqlServer() {
        assertThatThrownBy(() -> bancoDadosService.situacao()).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("só com o SQL Server");
        assertThatThrownBy(() -> bancoDadosService.fazerBackup()).hasMessageContaining("só com o SQL Server");
        assertThatThrownBy(() -> bancoDadosService.instalarObjetos()).hasMessageContaining("só com o SQL Server");
        assertThatThrownBy(() -> bancoDadosService.restaurar(new RestauracaoRequest("restaurar")))
                .hasMessage("Para restaurar, digite exatamente: RESTAURAR banco_fenix");
    }

    /** RN-SIS-03: a aba Banco de dados é só do Administrador. */
    @Test
    @WithMockUser(username = "operador.e3", roles = "OPERADOR")
    void bancoDeDados_soAdministrador() {
        assertThatThrownBy(() -> bancoDadosService.situacao()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bancoDadosService.restaurar(new RestauracaoRequest(BancoDadosService.CONFIRMACAO_RESTORE)))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Os scripts do grupo chegam ao classpath (pom.xml, E3-01) e se dividem pelo GO como no SSMS: cada script da
     * Etapa 3 tem o "use banco_fenix" e um lote "create or alter" por objeto do catálogo; o backup_restore.sql tem o
     * "use master" e as duas rotinas.
     */
    @Test
    void scripts_doGrupoDivididosPeloGo() {
        for (String script : Scripts.ETAPA3_EM_ORDEM) {
            List<String> lotes = ScriptsBanco.lotes(ScriptsBanco.ler(script));
            List<ObjetoBancoEnum> objetos = java.util.Arrays.stream(ObjetoBancoEnum.values())
                    .filter(o -> o.getScript().equals(script)).toList();
            assertThat(ScriptsBanco.ehUse(lotes.get(0))).as(script).isTrue();
            assertThat(lotes).as(script).hasSize(objetos.size() + 1);
            for (int i = 1; i < lotes.size(); i++) {
                assertThat(ScriptsBanco.semComentarios(lotes.get(i)).toLowerCase(Locale.ROOT)).startsWith("create or alter");
            }
            for (ObjetoBancoEnum o : objetos) {
                assertThat(String.join("\n", lotes).toLowerCase(Locale.ROOT)).as(o.getNome())
                        .contains("create or alter " + switch (o.getTipoSqlServer()) {
                            case "V" -> "view";
                            case "P" -> "procedure";
                            case "TR" -> "trigger";
                            default -> "function";
                        } + " " + o.getNome().toLowerCase(Locale.ROOT));
            }
        }
        List<String> backup = ScriptsBanco.lotes(ScriptsBanco.ler(Scripts.BACKUP_RESTORE));
        assertThat(backup).hasSize(3);
        assertThat(ScriptsBanco.ehUse(backup.get(0))).isTrue();
        assertThat(ScriptsBanco.semComentarios(backup.get(1)).toLowerCase(Locale.ROOT))
                .contains("backup database banco_fenix").contains("restore verifyonly").doesNotContain("restore database");
        assertThat(ScriptsBanco.semComentarios(backup.get(2)).toLowerCase(Locale.ROOT))
                .contains("set single_user with rollback immediate").contains("restore database banco_fenix")
                .contains("set multi_user");
    }
}
