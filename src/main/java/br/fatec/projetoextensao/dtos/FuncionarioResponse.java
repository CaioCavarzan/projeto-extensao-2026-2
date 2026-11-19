package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Funcionário devolvido pela API. salario vem null para OPERADOR.
 * temAcesso = tem usuário; acessoAtivo = usuário ativo (null quando não tem usuário).
 */

public record FuncionarioResponse(
        int codigo,
        String nome,
        String email,
        String telefone,
        EnderecoResponse endereco,
        String cpf,
        String cargo,
        BigDecimal salario,
        LocalDate dataAdmissao,
        boolean temAcesso,
        Boolean acessoAtivo,
        List<String> papeis,
        List<String> avisos) {
}
