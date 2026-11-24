package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * Etapa planejada no cronograma: código da etapa do item (ItensRotasProcessos) e quantidade.
 */

public record CronogramaEtapaRequest(Integer etapaCodigo, BigDecimal qtdPlanejado) {
}
