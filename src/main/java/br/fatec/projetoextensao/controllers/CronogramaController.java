package br.fatec.projetoextensao.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.CronogramaAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaRequest;
import br.fatec.projetoextensao.dtos.CronogramaRequest;
import br.fatec.projetoextensao.dtos.CronogramaResponse;
import br.fatec.projetoextensao.services.CronogramaService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): cronograma diário de produção. Um cronograma por dia (RN-CRO-02), gravado já com pelo
 * menos uma etapa (RN-CRO-01) e com o usuário logado (RN-CRO-04). A fila é renumerada 0, 1, 2... ao incluir,
 * remover ou reordenar (RN-CRI-07); POST /{codigo}/fila/sugerir ordena pela pontuação e pelo prazo (RN-CRI-09).
 */
@RestController
@RequestMapping("/api/cronogramas")
@RequiredArgsConstructor
public class CronogramaController {

    private final CronogramaService cronogramaService;

    @GetMapping
    public List<CronogramaResponse> listar() {
        return cronogramaService.listar();
    }

    @GetMapping("/dia")
    public CronogramaResponse doDia(@RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return cronogramaService.buscarDoDia(data);
    }

    @GetMapping("/{codigo}")
    public CronogramaResponse buscar(@PathVariable("codigo") int codigo) {
        return cronogramaService.buscar(codigo);
    }

    @PostMapping
    public ResponseEntity<CronogramaResponse> criar(@RequestBody CronogramaRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cronogramaService.criar(dto));
    }

    @PutMapping("/{codigo}")
    public CronogramaResponse atualizar(@PathVariable("codigo") int codigo, @RequestBody CronogramaAtualizacaoRequest dto) {
        return cronogramaService.atualizar(codigo, dto);
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        cronogramaService.excluir(codigo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{codigo}/etapas")
    public CronogramaResponse incluirEtapa(@PathVariable("codigo") int codigo, @RequestBody CronogramaEtapaRequest dto) {
        return cronogramaService.incluirEtapa(codigo, dto);
    }

    @DeleteMapping("/{codigo}/etapas/{etapaCodigo}")
    public CronogramaResponse removerEtapa(@PathVariable("codigo") int codigo, @PathVariable("etapaCodigo") int etapaCodigo) {
        return cronogramaService.removerEtapa(codigo, etapaCodigo);
    }

    @PutMapping("/{codigo}/fila")
    public CronogramaResponse reordenar(@PathVariable("codigo") int codigo, @RequestBody List<Integer> etapasNaOrdem) {
        return cronogramaService.reordenar(codigo, etapasNaOrdem);
    }

    @PostMapping("/{codigo}/fila/sugerir")
    public CronogramaResponse sugerirFila(@PathVariable("codigo") int codigo) {
        return cronogramaService.sugerirFila(codigo);
    }
}
