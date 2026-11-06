package br.fatec.projetoextensao.dtos;

import java.util.Map;

/**
 * INCLUÍDO (21/10/2026). Indicadores do topo do dashboard, calculados com os Services que já existem.
 * osPorStatus: quantidade de OS em cada status atual (RN-HOS-03).
 */
public record DashboardResumoResponse(Map<String, Long> osPorStatus, long osAbertas, long itensAtrasados, long etapasEmExecucao, long etapasAIniciar,
        long etapasConcluidasHoje, IndicadorRetrabalhoResponse retrabalhoMes, int etapasNoCronogramaHoje, long tintasSemEstoque) {
}
