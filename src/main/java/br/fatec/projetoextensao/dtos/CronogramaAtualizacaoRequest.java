package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;

/**
 * Alteração de data e observação do cronograma.
 */

public record CronogramaAtualizacaoRequest(LocalDate dataPlanejada, String observacao) {
}
