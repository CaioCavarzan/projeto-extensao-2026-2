package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (20/10/2026). Cadastro de funcionário (RN-FUN-01 a RN-FUN-11). Só ADMIN, porque o salário é obrigatório (RN-FUN-09).
 */
public record FuncionarioRequest(
        PessoaRequest pessoa,
        String cpf,
        String cargo,
        BigDecimal salario,
        LocalDate dataAdmissao) {
}
