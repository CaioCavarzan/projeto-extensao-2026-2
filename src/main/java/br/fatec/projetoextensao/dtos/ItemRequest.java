package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Item da OS. etapas = códigos das etapas da rota (RotasProcessos), na ordem de execução. 
 * tintaCodigo é opcional: a produção pode definir depois.
 * O código do item é gerado pelo banco.
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
