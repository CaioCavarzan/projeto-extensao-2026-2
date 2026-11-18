package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (18/11/2026) [E3-19]: uma linha da view dbo.vw_produtividade_funcionario: etapas concluídas num dia, por
 * um funcionário, num processo (Jateamento, Banho, Pintura...). Usada nos gráficos do dashboard, na aba
 * Produtividade da tela Produção e nos relatórios Produção diária e Produtividade.
 *
 * etapasRetrabalho: etapas que repetem um processo já feito antes no mesmo item (mesma regra do RN-ITE-06).
 * minutosTrabalhados e minutosMediosPorEtapa: diferença entre início e conclusão (DATEDIFF em minutos).
 */
public record ProdutividadeResponse(
        LocalDate data,
        int funcionarioCodigo,
        String funcionarioNome,
        String cargo,
        String processo,
        int etapasConcluidas,
        int itensAtendidos,
        int etapasRetrabalho,
        int minutosTrabalhados,
        BigDecimal minutosMediosPorEtapa) {
}
