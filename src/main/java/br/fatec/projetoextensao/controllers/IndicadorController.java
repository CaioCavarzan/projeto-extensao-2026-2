package br.fatec.projetoextensao.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.IndicadorRetrabalhoResponse;
import br.fatec.projetoextensao.dtos.ItemRetrabalhoResponse;
import br.fatec.projetoextensao.dtos.ProdutividadeResponse;
import br.fatec.projetoextensao.dtos.RetrabalhoMensalResponse;
import br.fatec.projetoextensao.services.IndicadorService;
import br.fatec.projetoextensao.services.ItemRotaProcessoService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): indicador do PDCA de GQA (RN-IRP-09): % de retrabalho = itens com alguma etapa repetida
 * ÷ itens executados no período × 100. Datas no formato AAAA-MM-DD; o período inclui as duas datas.
 * ALTERADO (18/11/2026) [E3-18]: novos /produtividade (view vw_produtividade_funcionario) e /retrabalho/mensal
 * (view vw_retrabalho_mensal), atendidos pelo IndicadorService.
 */
@RestController
@RequestMapping("/api/indicadores")
@RequiredArgsConstructor
public class IndicadorController {

    private final ItemRotaProcessoService itemRotaProcessoService;
    // INCLUÍDO (18/11/2026) [E3-18]: indicadores lidos das views da Etapa 3.
    private final IndicadorService indicadorService;

    @GetMapping("/retrabalho")
    public IndicadorRetrabalhoResponse retrabalho(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return itemRotaProcessoService.percentualRetrabalho(inicio, fim);
    }

    @GetMapping("/retrabalho/itens")
    public List<ItemRetrabalhoResponse> itensComRetrabalho(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return itemRotaProcessoService.itensComRetrabalho(inicio, fim);
    }

    /** INCLUÍDO (18/11/2026) [E3-18]: etapas concluídas por dia, funcionário e processo (vw_produtividade_funcionario). */
    @GetMapping("/produtividade")
    public List<ProdutividadeResponse> produtividade(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return indicadorService.produtividade(inicio, fim);
    }

    /** INCLUÍDO (18/11/2026) [E3-18]: % de retrabalho mês a mês (vw_retrabalho_mensal); padrão: últimos 12 meses. */
    @GetMapping("/retrabalho/mensal")
    public List<RetrabalhoMensalResponse> retrabalhoMensal(
            @RequestParam(name = "meses", defaultValue = "12") int meses) {
        return indicadorService.retrabalhoMensal(meses);
    }
}
