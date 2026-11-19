package br.fatec.projetoextensao.controllers;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.DocumentoRequest;
import br.fatec.projetoextensao.dtos.MensagensResponse;
import br.fatec.projetoextensao.services.PessoaService;
import lombok.RequiredArgsConstructor;

/**
 * Correção de CPF ou CNPJ digitado errado, só ADMIN. A correção vale para todos os papéis da
 * pessoa (o CPF do funcionário e o do cliente PF são o mesmo; o CNPJ do fornecedor e o do cliente PJ) 
 * e não aceita documento de outra pessoa. Responde com os papéis da pessoa.
 */

@RestController
@RequestMapping("/api/pessoas")
@RequiredArgsConstructor
public class PessoaController {

    private final PessoaService pessoaService;

    @PutMapping("/{codigo}/cpf")
    public MensagensResponse corrigirCpf(@PathVariable("codigo") int codigo, @RequestBody DocumentoRequest dto) {
        return new MensagensResponse(pessoaService.corrigirCpf(codigo, dto == null ? null : dto.documento()));
    }

    @PutMapping("/{codigo}/cnpj")
    public MensagensResponse corrigirCnpj(@PathVariable("codigo") int codigo, @RequestBody DocumentoRequest dto) {
        return new MensagensResponse(pessoaService.corrigirCnpj(codigo, dto == null ? null : dto.documento()));
    }
}
