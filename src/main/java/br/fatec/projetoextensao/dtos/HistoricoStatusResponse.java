package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * Linha do histórico de status (da OS ou do item).
 */

public record HistoricoStatusResponse(String status, LocalDateTime dataHora) {
}
