package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * Um evento da linha do tempo da OS, lido da função dbo.fn_auditoria_timeline_os
 * (aba Auditoria da tela da OS).
 *
 * categoria: "Ordem de Serviço", "Peça (Item)" ou "Etapa de Produção", como a função devolve.
 * responsavel: o que a função devolve. ATENÇÃO: para os eventos de status (da OS e dos itens), a função devolve o
 * login de quem ABRIU a OS, porque o schema não guarda quem mudou cada status (HistoricosStatusOs e
 * HistoricosStatusItens só têm a data e o status). Para não induzir a erro na tela, o Java acrescenta
 * papelResponsavel, que diz o que esse nome significa: "abriu a OS" ou "operador da etapa".
 */

public record EventoAuditoriaResponse(
        long sequencia,
        int osNumero,
        LocalDateTime dataHora,
        String categoria,
        Integer itemCodigo,
        String referencia,
        String evento,
        String responsavel,
        String papelResponsavel) {
}
