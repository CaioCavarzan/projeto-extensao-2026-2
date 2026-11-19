package br.fatec.projetoextensao.dtos;

import java.time.LocalDate;

/**
 * Pedido de replanejamento (POST /api/cronogramas/replanejar), que aciona a procedure
 * dbo.sp_replanejar_cronograma: as etapas ainda não concluídas do cronograma de dataOrigem passam a constar também
 * no cronograma de dataDestino. O usuário NÃO vem do front: é sempre o usuário logado.
 */

public record ReplanejamentoRequest(LocalDate dataOrigem, LocalDate dataDestino) {
}
