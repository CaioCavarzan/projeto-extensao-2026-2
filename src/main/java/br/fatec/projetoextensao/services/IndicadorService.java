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
 * Indicadores de produção lidos das views da Etapa 3, para a aba Produtividade da
 * tela Produção e para os relatórios (Produção diária, Produtividade e Retrabalho mensal).
 */

@Service
@RequiredArgsConstructor
public class IndicadorService {

    static final long MAX_DIAS_PRODUTIVIDADE = 366;
    static final int MAX_MESES_RETRABALHO = 36;

    private final RotinasBancoRepository rotinasBanco;

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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<RetrabalhoMensalResponse> retrabalhoMensal(int meses) {
        if (meses < 1 || meses > MAX_MESES_RETRABALHO) {
            throw new RegraNegocioException("Informe de 1 a " + MAX_MESES_RETRABALHO + " meses.");
        }
        return rotinasBanco.retrabalhoMensal(LocalDate.now().withDayOfMonth(1).minusMonths(meses - 1L));
    }
}
