package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultado do replanejamento. etapasMovidas é o parâmetro OUTPUT @etapas_movidas da
 * procedure; cronogramaDestino é o cronograma do dia de destino depois da procedure (null quando nada foi movido e o
 * dia não tinha cronograma); avisos explica o que aconteceu (ex.: nada a replanejar).
 */

public record ReplanejamentoResponse(
        LocalDate dataOrigem,
        LocalDate dataDestino,
        int etapasMovidas,
        CronogramaResponse cronogramaDestino,
        List<String> avisos) {
}
