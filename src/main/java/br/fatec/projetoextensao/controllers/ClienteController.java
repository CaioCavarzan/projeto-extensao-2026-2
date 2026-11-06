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

import br.fatec.projetoextensao.dtos.ClienteAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.ClientePJRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.services.ClienteService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): clientes. Todo cliente é PF ou PJ, nunca os dois (RN-CLI-01): por isso há uma rota de
 * cadastro para cada tipo, e o tipo não muda na alteração. Se o CPF já é de um funcionário (ou o CNPJ de um
 * fornecedor), o cadastro reaproveita a mesma pessoa e devolve o aviso em "avisos" (RN-PES-02 a RN-PES-04).
 * GET /semelhantes: aviso de possível duplicidade antes de cadastrar (RN-CLI-03). Excluir: só ADMIN (RN-CLI-02).
 */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    public List<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @GetMapping("/semelhantes")
    public List<ClienteResponse> semelhantes(@RequestParam("nome") String nome) {
        return clienteService.buscarSemelhantes(nome);
    }

    @GetMapping("/{codigo}")
    public ClienteResponse buscar(@PathVariable("codigo") int codigo) {
        return clienteService.buscar(codigo);
    }

    @PostMapping("/pf")
    public ResponseEntity<ClienteResponse> cadastrarPF(@RequestBody ClientePFRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.cadastrarPF(dto));
    }

    @PostMapping("/pj")
    public ResponseEntity<ClienteResponse> cadastrarPJ(@RequestBody ClientePJRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.cadastrarPJ(dto));
    }

    @PutMapping("/{codigo}")
    public ClienteResponse atualizar(@PathVariable("codigo") int codigo, @RequestBody ClienteAtualizacaoRequest dto) {
        return clienteService.atualizar(codigo, dto);
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        clienteService.excluir(codigo);
        return ResponseEntity.noContent().build();
    }
}
