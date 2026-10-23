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
import br.fatec.projetoextensao.services.ItemRotaProcessoService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): indicador do PDCA de GQA (RN-IRP-09): % de retrabalho = itens com alguma etapa repetida
 * ÷ itens executados no período × 100. Datas no formato AAAA-MM-DD; o período inclui as duas datas.
 */
@RestController
@RequestMapping("/api/indicadores")
@RequiredArgsConstructor
public class IndicadorController {

    private final ItemRotaProcessoService itemRotaProcessoService;

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
}
