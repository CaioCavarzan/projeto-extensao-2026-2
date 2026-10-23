package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (20/10/2026). Alteração do item. A OS não muda (RN-ITE-01); a tinta muda em ItemService.definirTinta; as etapas,
 * em ItemRotaProcessoService.
 */
public record ItemAtualizacaoRequest(
        String descricao,
        BigDecimal qtdTotal,
        String unidade,
        Integer corCodigo,
        LocalDate prazoEntrega,
        BigDecimal valorTotalItem) {
}
