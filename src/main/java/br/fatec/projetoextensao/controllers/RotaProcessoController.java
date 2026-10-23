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

import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.services.RotaProcessoService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): etapas do processo (tabela RotasProcessos: Jateamento, Banho e Pintura, RN-ROT-01).
 * A Pintura não é renomeada nem excluída, porque as regras do item a reconhecem pelo nome.
 * Listar: ADMIN e OPERADOR (as telas usam a lista nas caixas de seleção). Cadastrar, renomear e excluir: só ADMIN
 * (RN-SIS-03: alterar as listas de apoio). Registro em uso não é excluído (RN-SIS-12).
 */
@RestController
@RequestMapping("/api/etapas")
@RequiredArgsConstructor
public class RotaProcessoController {

    private final RotaProcessoService servico;

    @GetMapping
    public List<ItemListaResponse> listar() {
        return servico.listar();
    }

    @PostMapping
    public ResponseEntity<ItemListaResponse> cadastrar(@RequestBody NomeRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servico.cadastrar(dto));
    }

    @PutMapping("/{codigo}")
    public ItemListaResponse renomear(@PathVariable("codigo") int codigo, @RequestBody NomeRequest dto) {
        return servico.renomear(codigo, dto);
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        servico.excluir(codigo);
        return ResponseEntity.noContent().build();
    }
}
