package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alteração do item. A OS não muda; a tinta muda em ItemService.definirTinta; as etapas, em ItemRotaProcessoService.
 */

public record ItemAtualizacaoRequest(
        String descricao,
        BigDecimal qtdTotal,
        String unidade,
        Integer corCodigo,
        LocalDate prazoEntrega,
        BigDecimal valorTotalItem) {
}
