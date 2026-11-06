package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;

/**
 * INCLUÍDO (20/10/2026). Etapa planejada no cronograma: código da etapa do item (ItensRotasProcessos) e quantidade (RN-CRI-03 a RN-CRI-05).
 */
public record CronogramaEtapaRequest(Integer etapaCodigo, BigDecimal qtdPlanejado) {
}
