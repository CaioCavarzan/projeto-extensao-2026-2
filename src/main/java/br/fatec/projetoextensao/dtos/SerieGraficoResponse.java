package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Uma série de um gráfico do dashboard: nome e um valor para cada rótulo.
 */

public record SerieGraficoResponse(String nome, List<BigDecimal> valores) {
}
