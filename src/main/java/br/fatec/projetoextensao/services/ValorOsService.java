package br.fatec.projetoextensao.services;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.fatec.projetoextensao.dtos.PainelOsResponse;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Valor da OS.
 *
 * valor_os = soma de valor_total_item dos itens da OS, recalculada sempre que um item é incluído, alterado ou
 * cancelado; nunca é digitado. Item sem valor (NULL) e item cancelado ficam fora da soma; item ativo sem valor deixa
 * a OS com "valor incompleto".
 *
 * Fica separado do OrdemServicoService para o ItemService poder recalcular o valor sem depender dele (o
 * OrdemServicoService já depende do ItemService para abrir a OS com os itens).
 */

@Service
@RequiredArgsConstructor
public class ValorOsService {

    private static final Logger log = LoggerFactory.getLogger(ValorOsService.class);

    private final RotinasBancoRepository rotinasBanco;
    private final EntityManager entityManager;

    void sincronizar(OrdemServicoEntity os) {
        entityManager.flush();
        entityManager.refresh(os);
        BigDecimal conferido = rotinasBanco.valorOs(os.getNumero());
        if (os.getValorOS() == null || conferido.compareTo(os.getValorOS()) != 0) {
            log.warn("valor_os da OS {} gravado no banco ({}) difere de dbo.fn_valor_os ({}): confira se os triggers "
                    + "da Etapa 3 estão instalados e habilitados (Configurações > Banco de dados).",
                    os.getNumero(), os.getValorOS(), conferido);
        }
    }

    boolean valorIncompleto(OrdemServicoEntity os) {
        return rotinasBanco.painelOs(os.getNumero()).map(PainelOsResponse::valorIncompleto).orElse(false);
    }

}
