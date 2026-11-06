package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Item da OS (RN-ITE-01 a RN-ITE-29). etapas = códigos das etapas da rota (RotasProcessos), na ordem de execução
 * (RN-ITE-03: pelo menos uma). tintaCodigo é opcional: a produção pode definir depois (RN-ITE-11).
 * O código do item é gerado pelo banco (RN-ITE-09).
 */
public record ItemRequest(
        String descricao,
        BigDecimal qtdTotal,
        String unidade,
        Integer corCodigo,
        Integer tintaCodigo,
        LocalDate prazoEntrega,
        BigDecimal valorTotalItem,
        List<Integer> etapas) {
}
