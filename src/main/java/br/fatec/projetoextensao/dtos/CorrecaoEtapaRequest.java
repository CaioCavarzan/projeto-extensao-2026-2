package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * INCLUÍDO (20/10/2026). Correção manual das datas de uma etapa, só por ADMIN (RN-IRP-07).
 */
public record CorrecaoEtapaRequest(LocalDateTime dataInicio, LocalDateTime dataConclusao, Integer funcionarioCodigo) {
}
