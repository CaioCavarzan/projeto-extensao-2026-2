package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): valor da OS (RN-OS-13 a RN-OS-15 e RN-ITE-08).
 *
 * valor_os = soma de valor_total_item dos itens da OS, recalculada sempre que um item é incluído, alterado ou
 * cancelado; nunca é digitado. Item sem valor (NULL) e item cancelado ficam fora da soma; item ativo sem valor deixa
 * a OS com "valor incompleto" (RN-OS-15).
 *
 * Fica separado do OrdemServicoService para o ItemService poder recalcular o valor sem depender dele (o
 * OrdemServicoService já depende do ItemService para abrir a OS com os itens).
 */
@Service
@RequiredArgsConstructor
public class ValorOsService {

    private final ItemRepository itemRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final StatusService statusService;

    /** RN-OS-14: recalcula e grava valor_os (2 casas, RN-SIS-11). */
    void recalcular(OrdemServicoEntity os) {
        BigDecimal soma = BigDecimal.ZERO;
        for (ItemEntity item : itensQueContam(os)) {
            if (item.getValorTotalPeca() != null) {
                soma = soma.add(item.getValorTotalPeca());
            }
        }
        os.setValorOS(Valores.normalizar(soma));
        ordemServicoRepository.save(os);
    }

    /** RN-OS-15: há item não cancelado sem valor. */
    boolean valorIncompleto(OrdemServicoEntity os) {
        return itensQueContam(os).stream().anyMatch(i -> i.getValorTotalPeca() == null);
    }

    private List<ItemEntity> itensQueContam(OrdemServicoEntity os) {
        return itemRepository.findByOsOrderByCodigo(os).stream()
                .filter(i -> statusService.statusAtual(i) != StatusItemEnum.CANCELADA)
                .toList();
    }
}
