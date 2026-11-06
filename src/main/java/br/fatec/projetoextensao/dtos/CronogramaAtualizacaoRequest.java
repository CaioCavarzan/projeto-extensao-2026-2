package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;

/**
 * INCLUÍDO (20/10/2026). Alteração de data e observação do cronograma (RN-CRO-02, RN-CRO-06, RN-CRO-07).
 */
public record CronogramaAtualizacaoRequest(LocalDate dataPlanejada, String observacao) {
}
