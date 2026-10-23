package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (20/10/2026). Etapa na fila do cronograma.
 */
public record CronogramaEtapaResponse(
        int etapaCodigo,
        int itemCodigo,
        String itemDescricao,
        int osNumero,
        String rotaNome,
        int ordem,
        BigDecimal qtdPlanejado,
        int posicao,
        BigDecimal pontuacao,
        LocalDate prazoEntrega) {
}
