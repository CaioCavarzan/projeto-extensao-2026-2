package br.fatec.projetoextensao.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.CorrecaoEtapaRequest;
import br.fatec.projetoextensao.dtos.EtapaFilaResponse;
import br.fatec.projetoextensao.dtos.EtapaResponse;
import br.fatec.projetoextensao.dtos.IniciarEtapaRequest;
import br.fatec.projetoextensao.services.ItemRotaProcessoService;
import lombok.RequiredArgsConstructor;

/**
 * Execução das etapas da rota do item (tabela ItensRotasProcessos), na tela Produção.
 * Iniciar exige operador e as etapas anteriores concluídas; concluir exige a etapa iniciada; 
 * a hora vem do servidor. Retrabalho = nova etapa igual, no fim da rota.
 * Só etapa ainda não iniciada sai da rota. Corrigir datas: só ADMIN.
 */

@RestController
@RequestMapping("/api/etapas-item")
@RequiredArgsConstructor
public class ItemRotaProcessoController {

    private final ItemRotaProcessoService itemRotaProcessoService;

    @GetMapping("/fila")
    public List<EtapaFilaResponse> fila() {
        return itemRotaProcessoService.listarFila();
    }

    @PostMapping("/{codigo}/iniciar")
    public EtapaResponse iniciar(@PathVariable("codigo") int codigo, @RequestBody IniciarEtapaRequest dto) {
        return itemRotaProcessoService.iniciarEtapa(codigo, dto);
    }

    @PostMapping("/{codigo}/concluir")
    public EtapaResponse concluir(@PathVariable("codigo") int codigo) {
        return itemRotaProcessoService.concluirEtapa(codigo);
    }

    @PostMapping("/{codigo}/retrabalho")
    public List<EtapaResponse> retrabalho(@PathVariable("codigo") int codigo) {
        return itemRotaProcessoService.registrarRetrabalho(codigo);
    }

    @PutMapping("/{codigo}/datas")
    public EtapaResponse corrigirDatas(@PathVariable("codigo") int codigo, @RequestBody CorrecaoEtapaRequest dto) {
        return itemRotaProcessoService.corrigirDatas(codigo, dto);
    }

    @DeleteMapping("/{codigo}")
    public List<EtapaResponse> remover(@PathVariable("codigo") int codigo) {
        return itemRotaProcessoService.removerEtapa(codigo);
    }
}
