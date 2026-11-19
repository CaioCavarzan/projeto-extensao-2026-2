package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * Cronograma devolvido pela API, com a fila na ordem de posição.
 */

public record CronogramaResponse(
        int codigo,
        LocalDate dataPlanejada,
        String observacao,
        String registradoPor,
        List<CronogramaEtapaResponse> fila) {
}
