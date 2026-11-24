package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * % de retrabalho = itens com alguma etapa repetida / itens executados no período x 100.
 */

public record IndicadorRetrabalhoResponse(
        LocalDate inicio,
        LocalDate fim,
        long itensExecutados,
        long itensComRetrabalho,
        BigDecimal percentual) {
}
