package br.fatec.projetoextensao.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.DashboardResumoResponse;
import br.fatec.projetoextensao.dtos.GraficoResponse;
import br.fatec.projetoextensao.services.DashboardService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): dados da tela inicial. /resumo: indicadores do topo. /graficos/{nome}: dados de cada
 * gráfico (producao-diaria, retrabalho-mensal, os-por-status, etapas-por-funcionario). Os gráficos ainda respondem
 * disponivel = false: a programação dos dados fica para depois (ver DashboardService).
 * ALTERADO (18/11/2026) [E3-15]: os gráficos passaram a responder com dados das views da Etapa 3
 * (vw_produtividade_funcionario, vw_retrabalho_mensal e vw_painel_ordens_servico); os endereços não mudaram.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumo")
    public DashboardResumoResponse resumo() {
        return dashboardService.resumo();
    }

    @GetMapping("/graficos/{nome}")
    public GraficoResponse grafico(@PathVariable("nome") String nome) {
        return dashboardService.grafico(nome);
    }
}
