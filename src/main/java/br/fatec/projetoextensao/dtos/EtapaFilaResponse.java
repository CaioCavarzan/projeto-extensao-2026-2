package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Etapa da fila de produção: etapa ainda não concluída de item que não está encerrado.
 * podeIniciar e motivo são só uma orientação para a tela; quem decide é o ItemRotaProcessoService.iniciarEtapa.
 */

public record EtapaFilaResponse(int etapaCodigo, int itemCodigo, String itemDescricao, String itemStatus, int osNumero, String clienteNome,
        int ordem, int rotaCodigo, String rotaNome, LocalDateTime dataInicio, Integer funcionarioCodigo, String funcionarioNome,
        LocalDate prazoEntrega, boolean atrasado, BigDecimal qtdTotal, String unidade, boolean retrabalho, boolean podeIniciar,
        String motivo) {
}
