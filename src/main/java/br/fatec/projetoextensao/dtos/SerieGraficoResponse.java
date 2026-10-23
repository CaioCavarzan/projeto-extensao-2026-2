package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * INCLUÍDO (21/10/2026). Uma série de um gráfico do dashboard: nome e um valor para cada rótulo.
 */
public record SerieGraficoResponse(String nome, List<BigDecimal> valores) {
}
