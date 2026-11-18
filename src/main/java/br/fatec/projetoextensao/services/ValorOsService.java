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
 * INCLUÍDO (20/10/2026): valor da OS (RN-OS-13 a RN-OS-15 e RN-ITE-08).
 *
 * valor_os = soma de valor_total_item dos itens da OS, recalculada sempre que um item é incluído, alterado ou
 * cancelado; nunca é digitado. Item sem valor (NULL) e item cancelado ficam fora da soma; item ativo sem valor deixa
 * a OS com "valor incompleto" (RN-OS-15).
 *
 * Fica separado do OrdemServicoService para o ItemService poder recalcular o valor sem depender dele (o
 * OrdemServicoService já depende do ItemService para abrir a OS com os itens).
 *
 * ALTERADO (18/11/2026) [E3-08]: o valor deixou de ser calculado e gravado pelo Java. A regra continua a mesma, mas
 * agora mora no banco:
 * <ul>
 *   <li>dbo.fn_valor_os(@nr_os) faz a soma (itens não cancelados; valor null conta 0);</li>
 *   <li>trg_itens_sincronizar_valor_os grava valor_os a cada INSERT/UPDATE/DELETE em Itens;</li>
 *   <li>trg_historicos_status_itens_sincronizar_item_e_os grava valor_os a cada novo status de item (o item
 *       cancelado sai da soma);</li>
 *   <li>sp_cancelar_ordem_servico grava valor_os no fim do cancelamento.</li>
 * </ul>
 * Por que mudar: com o cálculo no banco, o valor fica certo mesmo quando o dado muda fora do sistema (SSMS, carga de
 * dados, outro programa), e a regra fica escrita uma vez só. Para o Java isso exige dois cuidados, feitos aqui:
 * (1) mandar ao banco as alterações pendentes do Hibernate (flush), para o trigger disparar; e (2) reler a OS
 * (refresh), porque o valor que o trigger gravou não aparece sozinho no objeto que está na memória.
 * Os métodos recalcular e itensQueContam foram removidos (ver os comentários REMOVIDO abaixo).
 */
@Service
@RequiredArgsConstructor
public class ValorOsService {

    private static final Logger log = LoggerFactory.getLogger(ValorOsService.class);

    // ALTERADO (18/11/2026) [E3-08]: eram ItemRepository, OrdemServicoRepository e StatusService, usados para somar os
    // itens em Java. Agora a classe só precisa do EntityManager (flush e refresh) e das rotinas do banco
    // (fn_valor_os e vw_painel_ordens_servico).
    private final RotinasBancoRepository rotinasBanco;
    private final EntityManager entityManager;

    // REMOVIDO (18/11/2026) [E3-08]: void recalcular(OrdemServicoEntity os). Somava valor_total_item dos itens não
    // cancelados em Java (uma consulta de status por item) e gravava os.setValorOS(...). Saiu porque o trigger já
    // grava o mesmo valor no mesmo momento; se o Java também gravasse, os dois disputariam a coluna e o UPDATE do
    // Hibernate (com o valor da memória) poderia desfazer o do trigger. Quem chamava recalcular agora chama
    // sincronizar (ItemService.incluir, atualizar e cancelar; OrdemServicoService.abrir).

    /**
     * INCLUÍDO (18/11/2026) [E3-08]: deixa a OS da memória igual à do banco depois que um item mudou.
     * <ol>
     *   <li>flush: envia o INSERT/UPDATE de Itens e HistoricosStatusItens que o Hibernate ainda segura; é nesse
     *       momento que os triggers rodam e gravam valor_os;</li>
     *   <li>refresh: relê a linha de OrdensDeServico, trazendo o valor_os gravado pelo trigger;</li>
     *   <li>conferência: compara com dbo.fn_valor_os. Se der diferente, algum trigger da Etapa 3 está faltando ou
     *       desabilitado; o sistema não corrige o valor por conta própria (o dono da coluna é o banco), só registra
     *       o aviso no log para o administrador conferir em Configurações > Banco de dados.</li>
     * </ol>
     */
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

    /**
     * RN-OS-15: há item não cancelado sem valor.
     * ALTERADO (18/11/2026) [E3-08]: era calculado em Java (itensQueContam + filtro valor null). Agora vem da coluna
     * valor_incompleto da view dbo.vw_painel_ordens_servico, que faz a mesma conta no banco; assim a tela da OS e a
     * lista de OS (que também lê a view) nunca discordam.
     */
    boolean valorIncompleto(OrdemServicoEntity os) {
        return rotinasBanco.painelOs(os.getNumero()).map(PainelOsResponse::valorIncompleto).orElse(false);
    }

    // REMOVIDO (18/11/2026) [E3-08]: private List<ItemEntity> itensQueContam(OrdemServicoEntity os). Listava os itens
    // não cancelados para a soma e para o valor incompleto; os dois cálculos passaram para o banco (fn_valor_os e
    // vw_painel_ordens_servico), então o método ficou sem uso.
}
