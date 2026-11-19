package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * Criação de cronograma com as etapas, na mesma transação.
 * O código é gerado pelo banco e o usuário que registra vem do login.
 */

public record CronogramaRequest(LocalDate dataPlanejada, String observacao, List<CronogramaEtapaRequest> etapas) {
}
