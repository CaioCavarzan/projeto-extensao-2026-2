package br.fatec.projetoextensao.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.AjusteEstoqueRequest;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.services.TintaService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): tintas e estoque. Cadastrar, alterar e ajustar o estoque: ADMIN e OPERADOR (RN-TIN-14).
 * Excluir: só ADMIN. POST /{codigo}/estoque recebe a quantidade do ajuste em kg: positiva para entrada de compra,
 * negativa para baixa; o saldo nunca fica negativo (RN-TIN-14, RN-TIN-15).
 */
@RestController
@RequestMapping("/api/tintas")
@RequiredArgsConstructor
public class TintaController {

    private final TintaService tintaService;

    @GetMapping
    public List<TintaResponse> listar() {
        return tintaService.listar();
    }

    @GetMapping("/{codigo}")
    public TintaResponse buscar(@PathVariable("codigo") int codigo) {
        return tintaService.buscar(codigo);
    }

    @PostMapping
    public ResponseEntity<TintaResponse> cadastrar(@RequestBody TintaRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tintaService.cadastrar(dto));
    }

    @PutMapping("/{codigo}")
    public TintaResponse atualizar(@PathVariable("codigo") int codigo, @RequestBody TintaRequest dto) {
        return tintaService.atualizar(codigo, dto);
    }

    @PostMapping("/{codigo}/estoque")
    public TintaResponse ajustarEstoque(@PathVariable("codigo") int codigo, @RequestBody AjusteEstoqueRequest dto) {
        return tintaService.ajustarEstoque(codigo, dto);
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        tintaService.excluir(codigo);
        return ResponseEntity.noContent().build();
    }
}
