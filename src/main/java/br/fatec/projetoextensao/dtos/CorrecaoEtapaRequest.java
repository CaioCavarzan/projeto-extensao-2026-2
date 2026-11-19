package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * Correção manual das datas de uma etapa, só por ADMIN.
 */

public record CorrecaoEtapaRequest(LocalDateTime dataInicio, LocalDateTime dataConclusao, Integer funcionarioCodigo) {
}
