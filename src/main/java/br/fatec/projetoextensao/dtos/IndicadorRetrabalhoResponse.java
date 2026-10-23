package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (20/10/2026). RN-IRP-09: % de retrabalho = itens com alguma etapa repetida / itens executados no período x 100.
 */
public record IndicadorRetrabalhoResponse(
        LocalDate inicio,
        LocalDate fim,
        long itensExecutados,
        long itensComRetrabalho,
        BigDecimal percentual) {
}
