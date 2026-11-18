package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.DashboardResumoResponse;
import br.fatec.projetoextensao.dtos.EtapaFilaResponse;
import br.fatec.projetoextensao.dtos.GraficoResponse;
import br.fatec.projetoextensao.dtos.IndicadorRetrabalhoResponse;
import br.fatec.projetoextensao.dtos.ProdutividadeResponse;
import br.fatec.projetoextensao.dtos.RetrabalhoMensalResponse;
import br.fatec.projetoextensao.dtos.SerieGraficoResponse;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.repositories.CronogramaItemRepository;
import br.fatec.projetoextensao.repositories.CronogramaRepository;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import br.fatec.projetoextensao.security.Perfis;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): dados do dashboard da tela inicial.
 *
 * resumo(): indicadores do topo da tela, calculados agora com os Services e repositories que já existem.
 *
 * grafico(nome): os gráficos ficam prontos na tela, mas os dados serão desenvolvidos depois (pedido de 21/10).
 * Enquanto isso, cada gráfico responde disponivel = false, e a tela mostra o quadro vazio com a mensagem.
 * Para ligar um gráfico, faça este método devolver, para o nome dele, o resultado de uma consulta (por exemplo, uma
 * view da Etapa 3 lida por um método @Query(nativeQuery = true) num repository; exemplo em docs/front-end.md),
 * preenchendo:
 * <ul>
 *   <li>rotulos: o eixo X (dias, meses, status ou funcionários);</li>
 *   <li>series: uma SerieGraficoResponse por linha/barra, com um valor por rótulo;</li>
 *   <li>disponivel = true e mensagem = null.</li>
 * </ul>
 * O front (js/paginas/dashboard.js) desenha qualquer resposta nesse formato, sem precisar mudar.
 *
 * ALTERADO (18/11/2026) [E3-15]: os 4 gráficos foram ligados às views da Etapa 3 (era o que o comentário acima
 * previa) e o resumo passou a ler as mesmas rotinas do banco:
 * <pre>
 *  gráfico / indicador         fonte no banco                       período
 *  producao-diaria (barra)     vw_produtividade_funcionario         hoje e os 29 dias anteriores
 *  etapas-por-funcionario      vw_produtividade_funcionario         hoje e os 29 dias anteriores (10 maiores)
 *  retrabalho-mensal (linha)   vw_retrabalho_mensal                 mês atual e os 11 anteriores
 *  os-por-status (rosca)       vw_painel_ordens_servico             todas as OS
 *  resumo: OS por status       vw_painel_ordens_servico
 *  resumo: itens atrasados     fn_itens_atrasados(hoje)
 *  resumo: concluídas hoje     vw_produtividade_funcionario (hoje)
 *  resumo: retrabalho do mês   vw_retrabalho_mensal (mês atual)
 * </pre>
 * Antes, o resumo percorria todas as OS (uma consulta de status por OS) e todos os itens com prazo vencido; agora
 * são poucas consultas prontas. Continuam em Java: a fila de produção (regras de "pode iniciar", que nenhuma view
 * tem), as etapas no cronograma de hoje e as tintas sem estoque.
 * A sugestão de view de cada gráfico (terceira posição do mapa GRAFICOS) virou o nome da view usada de verdade.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** INCLUÍDO (18/11/2026) [E3-15]: janela dos gráficos de produção: hoje e os 29 dias anteriores. */
    static final int DIAS_PRODUCAO = 30;
    /** INCLUÍDO (18/11/2026) [E3-15]: janela do gráfico de retrabalho: o mês atual e os 11 anteriores. */
    static final int MESES_RETRABALHO = 12;
    /** INCLUÍDO (18/11/2026) [E3-15]: no gráfico por funcionário, só os 10 que mais concluíram etapas (cabe na tela). */
    static final int MAX_FUNCIONARIOS = 10;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    /** Gráficos programados na tela inicial: nome na URL -> título, tipo e view que alimenta o gráfico. */
    private static final Map<String, String[]> GRAFICOS = new LinkedHashMap<>();

    static {
        // ALTERADO (18/11/2026) [E3-15]: a 3ª posição era a "sugestão de view"; agora é a view que alimenta o gráfico.
        GRAFICOS.put("producao-diaria", new String[] {"Produção diária", "barra", "vw_produtividade_funcionario"});
        GRAFICOS.put("retrabalho-mensal", new String[] {"% de retrabalho por mês", "linha", "vw_retrabalho_mensal"});
        GRAFICOS.put("os-por-status", new String[] {"Ordens de serviço por status", "rosca", "vw_painel_ordens_servico"});
        GRAFICOS.put("etapas-por-funcionario", new String[] {"Etapas concluídas por funcionário", "barra-horizontal",
                "vw_produtividade_funcionario"});
    }

    // ALTERADO (18/11/2026) [E3-15]: saíram OrdemServicoRepository, ItemRotaProcessoRepository, StatusService e
    // ItemService (usados para percorrer OS e itens em Java); entrou RotinasBancoRepository (views e functions).
    private final CronogramaRepository cronogramaRepository;
    private final CronogramaItemRepository cronogramaItemRepository;
    private final ItemRotaProcessoService itemRotaProcessoService;
    private final TintaService tintaService;
    private final RotinasBancoRepository rotinasBanco;

    /** Indicadores do topo do dashboard. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public DashboardResumoResponse resumo() {
        LocalDate hoje = LocalDate.now();
        Map<String, Long> osPorStatus = new LinkedHashMap<>();
        for (StatusOsEnum s : StatusOsEnum.values()) {
            osPorStatus.put(s.getNome(), 0L);
        }
        // ALTERADO (18/11/2026) [E3-15]: era um laço em Java em todas as OS com statusService.statusAtual(os); agora a
        // contagem por status vem pronta da view vw_painel_ordens_servico (status_atual = último do histórico, RN-HOS-03).
        long osAbertas = 0;
        for (Map.Entry<String, Long> e : rotinasBanco.osPorStatus().entrySet()) {
            if (e.getKey() == null) {
                continue; // OS sem histórico de status (não acontece pelo sistema): fora da contagem
            }
            osPorStatus.merge(e.getKey(), e.getValue(), Long::sum);
            if (!encerrado(e.getKey())) {
                osAbertas += e.getValue();
            }
        }
        // ALTERADO (18/11/2026) [E3-15]: era itemService.listarAtrasados().size(); a contagem é a da função
        // dbo.fn_itens_atrasados(hoje) (RN-ITE-22), a mesma lista mostrada no sino e nos alertas.
        long atrasados = rotinasBanco.itensAtrasados(hoje).size();
        List<EtapaFilaResponse> fila = itemRotaProcessoService.listarFila();
        long emExecucao = fila.stream().filter(e -> e.dataInicio() != null).count();
        // ALTERADO (18/11/2026) [E3-15]: era itemRotaProcessoRepository.countByDataConclusao...(hoje); agora soma
        // etapas_concluidas da view vw_produtividade_funcionario no dia de hoje (etapa concluída sempre tem operador,
        // CHECK ck_itens_rotas_processos_funcionario, então a contagem é a mesma).
        long concluidasHoje = rotinasBanco.produtividade(hoje, hoje).stream()
                .mapToLong(ProdutividadeResponse::etapasConcluidas).sum();
        // ALTERADO (18/11/2026) [E3-15]: era itemRotaProcessoService.percentualRetrabalho(1º dia do mês, hoje); agora é a
        // linha do mês atual da view vw_retrabalho_mensal (mesma regra: itens 'Executada' finalizados no mês).
        IndicadorRetrabalhoResponse retrabalhoMes = retrabalhoDoMes(hoje);
        int noCronograma = cronogramaRepository.findByDataPlanejada(hoje)
                .map(c -> cronogramaItemRepository.findByCronogramaOrderByPosicao(c).size())
                .orElse(0);
        long semEstoque = tintaService.listar().stream()
                .filter(t -> t.estoqueAtual() == null || t.estoqueAtual().compareTo(BigDecimal.ZERO) <= 0)
                .count();
        return new DashboardResumoResponse(osPorStatus, osAbertas, atrasados, emExecucao, fila.size() - emExecucao,
                concluidasHoje, retrabalhoMes, noCronograma, semEstoque);
    }

    /**
     * Dados de um gráfico do dashboard (ver a explicação no início da classe).
     * ALTERADO (18/11/2026) [E3-15]: era sempre disponivel = false ("Em desenvolvimento"); agora cada nome é lido da
     * view correspondente. Sem dados no período, a resposta vem com disponivel = true, rótulos vazios e a mensagem
     * do motivo (a tela mostra "Sem dados").
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public GraficoResponse grafico(String nome) {
        String[] g = GRAFICOS.get(nome);
        if (g == null) {
            throw new RecursoNaoEncontradoException("Gráfico não encontrado.");
        }
        return switch (nome) {
            case "producao-diaria" -> producaoDiaria(nome, g);
            case "retrabalho-mensal" -> retrabalhoMensal(nome, g);
            case "os-por-status" -> osPorStatus(nome, g);
            default -> etapasPorFuncionario(nome, g);
        };
    }

    // ------------------------------------------------------------------ gráficos (INCLUÍDO 18/11/2026 [E3-15])

    /** Etapas concluídas e etapas de retrabalho por dia, nos últimos 30 dias (dia sem produção aparece com zero). */
    private GraficoResponse producaoDiaria(String nome, String[] g) {
        LocalDate fim = LocalDate.now();
        LocalDate inicio = fim.minusDays(DIAS_PRODUCAO - 1L);
        List<ProdutividadeResponse> linhas = rotinasBanco.produtividade(inicio, fim);
        if (linhas.isEmpty()) {
            return semDados(nome, g, "Nenhuma etapa concluída nos últimos " + DIAS_PRODUCAO + " dias.");
        }
        Map<LocalDate, long[]> porDia = new LinkedHashMap<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            porDia.put(d, new long[2]);
        }
        for (ProdutividadeResponse p : linhas) {
            long[] soma = porDia.get(p.data());
            if (soma != null) {
                soma[0] += p.etapasConcluidas();
                soma[1] += p.etapasRetrabalho();
            }
        }
        List<String> rotulos = new ArrayList<>();
        List<BigDecimal> concluidas = new ArrayList<>();
        List<BigDecimal> retrabalho = new ArrayList<>();
        porDia.forEach((dia, soma) -> {
            rotulos.add(dia.format(DIA_MES));
            concluidas.add(BigDecimal.valueOf(soma[0]));
            retrabalho.add(BigDecimal.valueOf(soma[1]));
        });
        return new GraficoResponse(nome, g[0], g[1], rotulos, List.of(new SerieGraficoResponse("Etapas concluídas", concluidas),
                new SerieGraficoResponse("Retrabalho", retrabalho)), true, null);
    }

    /** % de retrabalho de cada mês (mês sem item executado fica sem ponto: não há base para o %). */
    private GraficoResponse retrabalhoMensal(String nome, String[] g) {
        LocalDate primeiro = LocalDate.now().withDayOfMonth(1).minusMonths(MESES_RETRABALHO - 1L);
        Map<LocalDate, BigDecimal> porMes = new LinkedHashMap<>();
        for (RetrabalhoMensalResponse r : rotinasBanco.retrabalhoMensal(primeiro)) {
            porMes.put(r.mesReferencia(), r.percentual());
        }
        if (porMes.isEmpty()) {
            return semDados(nome, g, "Nenhum item executado nos últimos " + MESES_RETRABALHO + " meses.");
        }
        List<String> rotulos = new ArrayList<>();
        List<BigDecimal> valores = new ArrayList<>();
        for (int i = 0; i < MESES_RETRABALHO; i++) {
            LocalDate mes = primeiro.plusMonths(i);
            rotulos.add(MESES[mes.getMonthValue() - 1] + "/" + String.format("%02d", mes.getYear() % 100));
            valores.add(porMes.get(mes)); // null = mês sem item executado
        }
        return new GraficoResponse(nome, g[0], g[1], rotulos, List.of(new SerieGraficoResponse("% de retrabalho", valores)),
                true, null);
    }

    /** OS por status atual (só os status que têm OS), na ordem do fluxo (StatusOsEnum). */
    private GraficoResponse osPorStatus(String nome, String[] g) {
        Map<String, Long> contagem = rotinasBanco.osPorStatus();
        List<String> rotulos = new ArrayList<>();
        List<BigDecimal> valores = new ArrayList<>();
        for (StatusOsEnum s : StatusOsEnum.values()) {
            Long qtd = contagem.remove(s.getNome());
            if (qtd != null && qtd > 0) {
                rotulos.add(s.getNome());
                valores.add(BigDecimal.valueOf(qtd));
            }
        }
        contagem.forEach((status, qtd) -> { // status gravado no banco que o sistema não conhece: aparece no fim
            if (status != null && qtd > 0) {
                rotulos.add(status);
                valores.add(BigDecimal.valueOf(qtd));
            }
        });
        if (rotulos.isEmpty()) {
            return semDados(nome, g, "Nenhuma ordem de serviço cadastrada.");
        }
        return new GraficoResponse(nome, g[0], g[1], rotulos, List.of(new SerieGraficoResponse("Ordens de serviço", valores)),
                true, null);
    }

    /** Etapas concluídas (e de retrabalho) por funcionário nos últimos 30 dias, do que mais concluiu para o que menos. */
    private GraficoResponse etapasPorFuncionario(String nome, String[] g) {
        LocalDate fim = LocalDate.now();
        List<ProdutividadeResponse> linhas = rotinasBanco.produtividade(fim.minusDays(DIAS_PRODUCAO - 1L), fim);
        if (linhas.isEmpty()) {
            return semDados(nome, g, "Nenhuma etapa concluída nos últimos " + DIAS_PRODUCAO + " dias.");
        }
        Map<Integer, long[]> porFuncionario = new LinkedHashMap<>();
        Map<Integer, String> nomes = new LinkedHashMap<>();
        for (ProdutividadeResponse p : linhas) {
            long[] soma = porFuncionario.computeIfAbsent(p.funcionarioCodigo(), c -> new long[2]);
            soma[0] += p.etapasConcluidas();
            soma[1] += p.etapasRetrabalho();
            nomes.put(p.funcionarioCodigo(), p.funcionarioNome());
        }
        List<Map.Entry<Integer, long[]>> ordem = new ArrayList<>(porFuncionario.entrySet());
        ordem.sort(Comparator.comparingLong((Map.Entry<Integer, long[]> e) -> e.getValue()[0]).reversed()
                .thenComparing(e -> nomes.get(e.getKey())));
        List<String> rotulos = new ArrayList<>();
        List<BigDecimal> concluidas = new ArrayList<>();
        List<BigDecimal> retrabalho = new ArrayList<>();
        ordem.stream().limit(MAX_FUNCIONARIOS).forEach(e -> {
            rotulos.add(nomes.get(e.getKey()));
            concluidas.add(BigDecimal.valueOf(e.getValue()[0]));
            retrabalho.add(BigDecimal.valueOf(e.getValue()[1]));
        });
        return new GraficoResponse(nome, g[0], g[1], rotulos, List.of(new SerieGraficoResponse("Etapas concluídas", concluidas),
                new SerieGraficoResponse("Retrabalho", retrabalho)), true, null);
    }

    // ------------------------------------------------------------------ apoio

    /** INCLUÍDO (18/11/2026) [E3-15]: gráfico ligado, mas sem dados no período (a tela mostra "Sem dados" e o motivo). */
    private static GraficoResponse semDados(String nome, String[] g, String motivo) {
        return new GraficoResponse(nome, g[0], g[1], List.of(), List.of(), true, motivo);
    }

    /** INCLUÍDO (18/11/2026) [E3-15]: linha do mês atual da vw_retrabalho_mensal (zeros se nenhum item foi executado). */
    private IndicadorRetrabalhoResponse retrabalhoDoMes(LocalDate hoje) {
        LocalDate inicio = hoje.withDayOfMonth(1);
        return rotinasBanco.retrabalhoMensal(inicio).stream()
                .filter(r -> inicio.equals(r.mesReferencia()))
                .findFirst()
                .map(r -> new IndicadorRetrabalhoResponse(inicio, hoje, r.itensExecutados(), r.itensComRetrabalho(),
                        r.percentual()))
                .orElse(new IndicadorRetrabalhoResponse(inicio, hoje, 0, 0, BigDecimal.ZERO.setScale(2)));
    }

    /** INCLUÍDO (18/11/2026) [E3-15]: status encerrado pelo nome, sem lançar erro para nome desconhecido. */
    private static boolean encerrado(String status) {
        return StatusOsEnum.CONCLUIDA.getNome().equalsIgnoreCase(status)
                || StatusOsEnum.CANCELADA.getNome().equalsIgnoreCase(status);
    }
}
