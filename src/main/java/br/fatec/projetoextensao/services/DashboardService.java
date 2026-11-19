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
 * Dados do dashboard da tela inicial.
 */

@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int DIAS_PRODUCAO = 30;
    static final int MESES_RETRABALHO = 12;
    static final int MAX_FUNCIONARIOS = 10;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    private static final Map<String, String[]> GRAFICOS = new LinkedHashMap<>();

    static {
        GRAFICOS.put("producao-diaria", new String[] {"Produção diária", "barra", "vw_produtividade_funcionario"});
        GRAFICOS.put("retrabalho-mensal", new String[] {"% de retrabalho por mês", "linha", "vw_retrabalho_mensal"});
        GRAFICOS.put("os-por-status", new String[] {"Ordens de serviço por status", "rosca", "vw_painel_ordens_servico"});
        GRAFICOS.put("etapas-por-funcionario", new String[] {"Etapas concluídas por funcionário", "barra-horizontal",
                "vw_produtividade_funcionario"});
    }

    private final CronogramaRepository cronogramaRepository;
    private final CronogramaItemRepository cronogramaItemRepository;
    private final ItemRotaProcessoService itemRotaProcessoService;
    private final TintaService tintaService;
    private final RotinasBancoRepository rotinasBanco;

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public DashboardResumoResponse resumo() {
        LocalDate hoje = LocalDate.now();
        Map<String, Long> osPorStatus = new LinkedHashMap<>();
        for (StatusOsEnum s : StatusOsEnum.values()) {
            osPorStatus.put(s.getNome(), 0L);
        }
        long osAbertas = 0;
        for (Map.Entry<String, Long> e : rotinasBanco.osPorStatus().entrySet()) {
            if (e.getKey() == null) {
                continue; 
            }
            osPorStatus.merge(e.getKey(), e.getValue(), Long::sum);
            if (!encerrado(e.getKey())) {
                osAbertas += e.getValue();
            }
        }
        long atrasados = rotinasBanco.itensAtrasados(hoje).size();
        List<EtapaFilaResponse> fila = itemRotaProcessoService.listarFila();
        long emExecucao = fila.stream().filter(e -> e.dataInicio() != null).count();
        long concluidasHoje = rotinasBanco.produtividade(hoje, hoje).stream()
                .mapToLong(ProdutividadeResponse::etapasConcluidas).sum();
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
        contagem.forEach((status, qtd) -> { 
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

    private static GraficoResponse semDados(String nome, String[] g, String motivo) {
        return new GraficoResponse(nome, g[0], g[1], List.of(), List.of(), true, motivo);
    }

    private IndicadorRetrabalhoResponse retrabalhoDoMes(LocalDate hoje) {
        LocalDate inicio = hoje.withDayOfMonth(1);
        return rotinasBanco.retrabalhoMensal(inicio).stream()
                .filter(r -> inicio.equals(r.mesReferencia()))
                .findFirst()
                .map(r -> new IndicadorRetrabalhoResponse(inicio, hoje, r.itensExecutados(), r.itensComRetrabalho(),
                        r.percentual()))
                .orElse(new IndicadorRetrabalhoResponse(inicio, hoje, 0, 0, BigDecimal.ZERO.setScale(2)));
    }

    private static boolean encerrado(String status) {
        return StatusOsEnum.CONCLUIDA.getNome().equalsIgnoreCase(status)
                || StatusOsEnum.CANCELADA.getNome().equalsIgnoreCase(status);
    }
}
