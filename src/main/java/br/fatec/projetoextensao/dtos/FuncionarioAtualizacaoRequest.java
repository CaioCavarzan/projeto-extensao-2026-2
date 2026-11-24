package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alteração de funcionário (só ADMIN). O CPF é corrigido em PessoaService.corrigirCpf.
 */

public record FuncionarioAtualizacaoRequest(
        PessoaRequest pessoa,
        String cargo,
        BigDecimal salario,
        LocalDate dataAdmissao) {
}
