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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.FornecedorAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.FornecedorRequest;
import br.fatec.projetoextensao.dtos.FornecedorResponse;
import br.fatec.projetoextensao.services.FornecedorService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): fornecedores. Cadastrar e alterar: ADMIN e OPERADOR. Inativar, reativar e excluir: só
 * ADMIN (RN-SIS-03 e RN-SIS-12). Fornecedor nunca é funcionário (RN-PES-05); se o CNPJ já é de um cliente PJ, a mesma
 * pessoa ganha o papel de fornecedor (RN-PES-02). ?somenteAtivos=true lista só os ativos (para escolher na tinta).
 */
@RestController
@RequestMapping("/api/fornecedores")
@RequiredArgsConstructor
public class FornecedorController {

    private final FornecedorService fornecedorService;

    @GetMapping
    public List<FornecedorResponse> listar(@RequestParam(name = "somenteAtivos", defaultValue = "false") boolean somenteAtivos) {
        return fornecedorService.listar(somenteAtivos);
    }

    @GetMapping("/{codigo}")
    public FornecedorResponse buscar(@PathVariable("codigo") int codigo) {
        return fornecedorService.buscar(codigo);
    }

    @PostMapping
    public ResponseEntity<FornecedorResponse> cadastrar(@RequestBody FornecedorRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fornecedorService.cadastrar(dto));
    }

    @PutMapping("/{codigo}")
    public FornecedorResponse atualizar(@PathVariable("codigo") int codigo, @RequestBody FornecedorAtualizacaoRequest dto) {
        return fornecedorService.atualizar(codigo, dto);
    }

    @PostMapping("/{codigo}/inativar")
    public FornecedorResponse inativar(@PathVariable("codigo") int codigo) {
        return fornecedorService.inativar(codigo);
    }

    @PostMapping("/{codigo}/reativar")
    public FornecedorResponse reativar(@PathVariable("codigo") int codigo) {
        return fornecedorService.reativar(codigo);
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        fornecedorService.excluir(codigo);
        return ResponseEntity.noContent().build();
    }
}
