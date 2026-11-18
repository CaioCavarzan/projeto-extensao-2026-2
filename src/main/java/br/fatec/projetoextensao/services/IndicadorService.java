package br.fatec.projetoextensao.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ProdutividadeResponse;
import br.fatec.projetoextensao.dtos.RetrabalhoMensalResponse;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import br.fatec.projetoextensao.security.Perfis;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (18/11/2026) [E3-15]: indicadores de produção lidos das views da Etapa 3, para a aba Produtividade da
 * tela Produção e para os relatórios (Produção diária, Produtividade e Retrabalho mensal).
 * <ul>
 *   <li>produtividade(inicio, fim): linhas da dbo.vw_produtividade_funcionario no período;</li>
 *   <li>retrabalhoMensal(meses): linhas da dbo.vw_retrabalho_mensal dos últimos meses.</li>
 * </ul>
 * O % de retrabalho de um período qualquer (ex.: de 05/11 a 18/11) continua em
 * ItemRotaProcessoService.percentualRetrabalho, em Java: a view agrupa por mês e não responde a um intervalo de dias.
 */
@Service
@RequiredArgsConstructor
public class IndicadorService {

    /** Limite do período de produtividade: um ano (evita consulta enorme por engano na tela). */
    static final long MAX_DIAS_PRODUTIVIDADE = 366;
    /** Limite de meses do retrabalho mensal: três anos. */
    static final int MAX_MESES_RETRABALHO = 36;

    private final RotinasBancoRepository rotinasBanco;

    /** Etapas concluídas por dia, funcionário e processo entre inicio e fim (inclusive). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ProdutividadeResponse> produtividade(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new RegraNegocioException("Informe o período: data inicial e data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= MAX_DIAS_PRODUTIVIDADE) {
            throw new RegraNegocioException("Escolha um período de até um ano.");
        }
        return rotinasBanco.produtividade(inicio, fim);
    }

    /** % de retrabalho de cada mês, do mês de (hoje - meses + 1) até o atual. Mês sem item executado não aparece. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<RetrabalhoMensalResponse> retrabalhoMensal(int meses) {
        if (meses < 1 || meses > MAX_MESES_RETRABALHO) {
            throw new RegraNegocioException("Informe de 1 a " + MAX_MESES_RETRABALHO + " meses.");
        }
        return rotinasBanco.retrabalhoMensal(LocalDate.now().withDayOfMonth(1).minusMonths(meses - 1L));
    }
}
