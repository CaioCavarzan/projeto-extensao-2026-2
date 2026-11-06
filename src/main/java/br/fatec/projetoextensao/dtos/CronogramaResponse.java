package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Cronograma devolvido pela API, com a fila na ordem de posição (RN-CRI-07).
 */
public record CronogramaResponse(
        int codigo,
        LocalDate dataPlanejada,
        String observacao,
        String registradoPor,
        List<CronogramaEtapaResponse> fila) {
}
