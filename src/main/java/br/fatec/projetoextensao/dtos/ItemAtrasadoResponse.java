package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (18/11/2026) [E3-19]: uma linha da função dbo.fn_itens_atrasados (RN-ITE-22): peça com prazo vencido e
 * ainda não encerrada.
 *
 * Por que um DTO novo: a função devolve dados que o ItemResponse não tinha (cliente, dias de atraso, próxima etapa
 * pendente e o operador dela) e não devolve o que ele tinha (rota completa, fotos, valor). Os campos codigo,
 * osNumero, descricao, qtdTotal, unidade, prazoEntrega e status ficaram com o MESMO nome do ItemResponse de
 * propósito: o sino de alertas, o dashboard e o relatório, que já liam GET /api/itens/atrasados, continuam
 * funcionando sem mudar esses campos.
 *
 * Colunas da função: item_codigo, nr_os, cliente_nome, item_descricao, qtd_total, unidade, prazo_entrega,
 * dias_atraso, status_atual, etapa_pendente e operador_etapa (as duas últimas são null quando todas as etapas foram
 * concluídas ou quando a próxima etapa ainda não tem operador).
 */
public record ItemAtrasadoResponse(
        int codigo,
        int osNumero,
        String clienteNome,
        String descricao,
        BigDecimal qtdTotal,
        String unidade,
        LocalDate prazoEntrega,
        int diasAtraso,
        String status,
        String etapaPendente,
        String operadorEtapa) {
}
