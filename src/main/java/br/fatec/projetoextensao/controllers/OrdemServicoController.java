package br.fatec.projetoextensao.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.HistoricoStatusResponse;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.PrioridadeRequest;
import br.fatec.projetoextensao.services.ItemService;
import br.fatec.projetoextensao.services.OrdemServicoService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): ordens de serviço. POST abre a OS com os itens na mesma transação (RN-OS-01 e RN-SIS-04);
 * número, usuário, data de entrada e valor não vêm no corpo (RN-SIS-05). Mudanças de status pelas ações
 * pausar, retomar, concluir (RN-OS-04) e cancelar (só ADMIN, RN-OS-05), conforme o fluxo de status (RN-HOS-02).
 * POST /{numero}/itens inclui um item numa OS aberta (RN-OS-07).
 */
@RestController
@RequestMapping("/api/ordens")
@RequiredArgsConstructor
public class OrdemServicoController {

    private final OrdemServicoService ordemServicoService;
    private final ItemService itemService;

    @GetMapping
    public List<OsResponse> listar() {
        return ordemServicoService.listar();
    }

    @GetMapping("/{numero}")
    public OsResponse buscar(@PathVariable("numero") int numero) {
        return ordemServicoService.buscar(numero);
    }

    @PostMapping
    public ResponseEntity<OsResponse> abrir(@RequestBody AbrirOsRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordemServicoService.abrir(dto));
    }

    @PutMapping("/{numero}/prioridade")
    public OsResponse alterarPrioridade(@PathVariable("numero") int numero, @RequestBody PrioridadeRequest dto) {
        return ordemServicoService.alterarPrioridade(numero, dto == null ? null : dto.prioridade());
    }

    @PostMapping("/{numero}/pausar")
    public OsResponse pausar(@PathVariable("numero") int numero) {
        return ordemServicoService.pausar(numero);
    }

    @PostMapping("/{numero}/retomar")
    public OsResponse retomar(@PathVariable("numero") int numero) {
        return ordemServicoService.retomar(numero);
    }

    @PostMapping("/{numero}/concluir")
    public OsResponse concluir(@PathVariable("numero") int numero) {
        return ordemServicoService.concluir(numero);
    }

    @PostMapping("/{numero}/cancelar")
    public OsResponse cancelar(@PathVariable("numero") int numero) {
        return ordemServicoService.cancelar(numero);
    }

    @GetMapping("/{numero}/historico")
    public List<HistoricoStatusResponse> historico(@PathVariable("numero") int numero) {
        return ordemServicoService.historico(numero);
    }

    @PostMapping("/{numero}/itens")
    public ResponseEntity<ItemResponse> incluirItem(@PathVariable("numero") int numero, @RequestBody ItemRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.incluir(numero, dto));
    }
}
