package br.fatec.projetoextensao.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.services.StatusService;
import lombok.RequiredArgsConstructor;

/**
 * Listas fixas de status, só leitura: o status muda pelas ações da
 * OS e do item (pausar, retomar, concluir, cancelar), conforme a aba "Fluxo de status" da planilha.
 */

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @GetMapping("/os")
    public List<ItemListaResponse> statusOs() {
        return statusService.listarStatusOs();
    }

    @GetMapping("/itens")
    public List<ItemListaResponse> statusItens() {
        return statusService.listarStatusItens();
    }
}
