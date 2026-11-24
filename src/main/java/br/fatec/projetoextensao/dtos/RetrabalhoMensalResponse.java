package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha da view dbo.vw_retrabalho_mensal, o indicador mensal do PDCA:
 * itens executados no mês (pela data_finalizacao), quantos tiveram etapa repetida, o % de retrabalho e o valor dos
 * itens executados. Meses sem item executado não aparecem na view.
 */

public record RetrabalhoMensalResponse(
        LocalDate mesReferencia,
        int ano,
        int mes,
        int itensExecutados,
        int itensComRetrabalho,
        BigDecimal percentual,
        BigDecimal valorExecutado) {
}
