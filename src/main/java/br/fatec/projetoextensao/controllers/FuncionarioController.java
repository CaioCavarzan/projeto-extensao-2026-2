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

import br.fatec.projetoextensao.dtos.FuncionarioAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.FuncionarioRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.services.FuncionarioService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): funcionários. Cadastrar, alterar, desligar e excluir: só ADMIN (o cadastro tem o salário,
 * RN-FUN-09). Listar e buscar: ADMIN e OPERADOR, mas o salário só vai para o ADMIN. Funcionário nunca é fornecedor
 * (RN-PES-05); se o CPF já é de um cliente PF, a mesma pessoa ganha o papel de funcionário (RN-PES-02).
 */
@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
public class FuncionarioController {

    private final FuncionarioService funcionarioService;

    @GetMapping
    public List<FuncionarioResponse> listar() {
        return funcionarioService.listar();
    }

    @GetMapping("/{codigo}")
    public FuncionarioResponse buscar(@PathVariable("codigo") int codigo) {
        return funcionarioService.buscar(codigo);
    }

    @PostMapping
    public ResponseEntity<FuncionarioResponse> cadastrar(@RequestBody FuncionarioRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(funcionarioService.cadastrar(dto));
    }

    @PutMapping("/{codigo}")
    public FuncionarioResponse atualizar(@PathVariable("codigo") int codigo, @RequestBody FuncionarioAtualizacaoRequest dto) {
        return funcionarioService.atualizar(codigo, dto);
    }

    @PostMapping("/{codigo}/desligar")
    public FuncionarioResponse desligar(@PathVariable("codigo") int codigo) {
        return funcionarioService.desligar(codigo);
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        funcionarioService.excluir(codigo);
        return ResponseEntity.noContent().build();
    }
}
