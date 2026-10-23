package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * INCLUÍDO (20/10/2026). Linha do histórico de status (da OS ou do item).
 */
public record HistoricoStatusResponse(String status, LocalDateTime dataHora) {
}
