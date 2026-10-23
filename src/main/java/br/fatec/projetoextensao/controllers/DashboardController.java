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
