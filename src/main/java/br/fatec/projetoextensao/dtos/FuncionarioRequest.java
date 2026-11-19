package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cadastro de funcionário. Só ADMIN, porque o salário é obrigatório.
 */

public record FuncionarioRequest(
        PessoaRequest pessoa,
        String cpf,
        String cargo,
        BigDecimal salario,
        LocalDate dataAdmissao) {
}
