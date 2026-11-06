package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Criação de cronograma com as etapas, na mesma transação (RN-CRO-01, RN-SIS-04).
 * O código é gerado pelo banco (RN-CRO-03) e o usuário que registra vem do login (RN-CRO-04).
 */
public record CronogramaRequest(LocalDate dataPlanejada, String observacao, List<CronogramaEtapaRequest> etapas) {
}
