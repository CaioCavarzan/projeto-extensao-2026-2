package br.fatec.projetoextensao.dtos;

/**
 * CPF ou CNPJ corrigido por ADMIN (PessoaService.corrigirCpf / corrigirCnpj).
 */

public record DocumentoRequest(String documento) {
}
