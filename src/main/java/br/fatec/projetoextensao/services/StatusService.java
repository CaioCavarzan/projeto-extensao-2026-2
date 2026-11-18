package br.fatec.projetoextensao.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.entities.HistoricoStatusItemEntity;
import br.fatec.projetoextensao.entities.HistoricoStatusOsEntity;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.StatusItemEntity;
import br.fatec.projetoextensao.entities.StatusOsEntity;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.HistoricoStatusItemRepository;
import br.fatec.projetoextensao.repositories.HistoricoStatusOsRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.StatusItemRepository;
import br.fatec.projetoextensao.repositories.StatusOsRepository;
import br.fatec.projetoextensao.security.Perfis;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): status da OS e do item.
 *
 * Tabelas: StatusOs, StatusItens, HistoricosStatusOs, HistoricosStatusItens.
 * Regras: RN-SOS-01, RN-SIT-01 (listas fixas), RN-HOS-01 a RN-HOS-04 e RN-HIT-01 a RN-HIT-04 (histórico e
 * transições), RN-OS-02, RN-ITE-02 (status inicial), RN-OS-03 (OS concluída não reabre), RN-ITE-26 (data de
 * finalização) e o gatilho Pendente -> Aguardando execução da aba "Fluxo de status".
 *
 * O histórico só recebe inclusões (RN-HOS-01 e RN-HIT-01): esta classe não tem método que altere ou exclua linhas
 * do histórico, e os outros Services só mudam status por aqui. As listas de status também não têm métodos de
 * alteração, então nenhum status usado no fluxo é renomeado ou excluído pelo sistema (RN-SOS-01, RN-SIT-01).
 *
 * ALTERADO (18/11/2026) [E3-09]: com a Etapa 3, o banco também garante essas regras e completa o que esta classe
 * grava:
 * <ul>
 *   <li>trg_historicos_status_os_proteger recusa UPDATE/DELETE no histórico da OS (THROW 50101) e novo status em OS
 *       já Concluída ou Cancelada (THROW 50102): a mesma RN-HOS-01 e RN-OS-03 daqui, agora também para quem mexer
 *       no banco por fora do sistema;</li>
 *   <li>trg_historicos_status_itens_sincronizar_item_e_os recusa UPDATE/DELETE no histórico do item (THROW 50120),
 *       preenche Itens.data_finalizacao quando entra 'Executada' (antes era feito aqui, RN-ITE-26) e recalcula
 *       valor_os;</li>
 *   <li>o cancelamento da OS grava 'Cancelada' na OS e nos itens pela procedure sp_cancelar_ordem_servico (ver
 *       OrdemServicoService.cancelar); conferirMudanca confere as regras antes.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusOsRepository statusOsRepository;
    private final StatusItemRepository statusItemRepository;
    private final HistoricoStatusOsRepository historicoStatusOsRepository;
    private final HistoricoStatusItemRepository historicoStatusItemRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    // REMOVIDO (18/11/2026) [E3-09]: private final ItemRepository itemRepository. Só servia para gravar a
    // data_finalizacao do item, que agora é gravada pelo trigger.
    // INCLUÍDO (18/11/2026) [E3-09]: EntityManager, para o flush e o refresh do item depois do status 'Executada'.
    private final EntityManager entityManager;

    // ------------------------------------------------------------------ listas (só leitura)

    /** RN-SOS-01: lista de status da OS. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listarStatusOs() {
        return statusOsRepository.findAllByOrderByCodigo().stream()
                .map(s -> new ItemListaResponse(s.getCodigo(), s.getStatus()))
                .toList();
    }

    /** RN-SIT-01: lista de status do item. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listarStatusItens() {
        return statusItemRepository.findAllByOrderByCodigo().stream()
                .map(s -> new ItemListaResponse(s.getCodigo(), s.getStatus()))
                .toList();
    }

    // ------------------------------------------------------------------ uso interno dos outros Services

    /** RN-HOS-06: o status gravado no histórico da OS é sempre um dos cadastrados em StatusOs (FK). */
    StatusOsEntity entidade(StatusOsEnum status) {
        return statusOsRepository.findByStatus(status.getNome())
                .orElseThrow(() -> new IllegalStateException("Status da OS não cadastrado: " + status.getNome()));
    }

    /** RN-HIT-06: o status gravado no histórico do item é sempre um dos cadastrados em StatusItens (FK). */
    StatusItemEntity entidade(StatusItemEnum status) {
        return statusItemRepository.findByStatus(status.getNome())
                .orElseThrow(() -> new IllegalStateException("Status do item não cadastrado: " + status.getNome()));
    }

    /** RN-HOS-03: status atual da OS = registro mais recente do histórico (a OS não tem coluna de status). */
    StatusOsEnum statusAtual(OrdemServicoEntity os) {
        return historicoStatusOsRepository.findTopByOsOrderByDataHoraDesc(os)
                .map(h -> StatusOsEnum.doNome(h.getStatus().getStatus()))
                .orElseThrow(() -> new IllegalStateException("OS sem status no histórico: " + os.getNumero()));
    }

    /** RN-HIT-03: status atual do item = registro mais recente do histórico. */
    StatusItemEnum statusAtual(ItemEntity item) {
        return historicoStatusItemRepository.findTopByItemOrderByDataHoraDesc(item)
                .map(h -> StatusItemEnum.doNome(h.getStatusItem().getStatus()))
                .orElseThrow(() -> new IllegalStateException("Item sem status no histórico: " + item.getCodigo()));
    }

    /** RN-OS-02: toda OS nasce com o status 'Criada' no histórico, na mesma transação da abertura. */
    void registrarInicial(OrdemServicoEntity os) {
        gravar(os, StatusOsEnum.CRIADA, proximaDataHora(null));
    }

    /** RN-ITE-02: todo item nasce com o status 'Pendente' no histórico, na mesma transação da inclusão. */
    void registrarInicial(ItemEntity item) {
        gravar(item, StatusItemEnum.PENDENTE, proximaDataHora(null));
    }

    /**
     * Muda o status da OS conferindo as regras:
     * RN-HOS-04 (não repete o status atual), RN-OS-03 (Concluída não reabre) e RN-HOS-02 (só transições permitidas).
     */
    void mudarStatus(OrdemServicoEntity os, StatusOsEnum novo) {
        // ALTERADO (18/11/2026) [E3-12]: as conferências que ficavam aqui passaram para conferirMudanca (logo abaixo),
        // sem mudar nada nelas, para o cancelamento da OS poder conferir as mesmas regras antes de chamar a procedure
        // dbo.sp_cancelar_ordem_servico (que grava o status 'Cancelada' ela mesma).
        HistoricoStatusOsEntity ultimo = conferirMudanca(os, novo);
        gravar(os, novo, proximaDataHora(ultimo.getDataHora()));
    }

    /**
     * INCLUÍDO (18/11/2026) [E3-12]: confere RN-HOS-04 (não repete o status atual), RN-OS-03 (Concluída não reabre)
     * e RN-HOS-02 (só transições permitidas) SEM gravar nada; devolve o último registro do histórico.
     * Por que separado: a procedure de cancelamento também confere "já cancelada" (THROW 50012) e "concluída"
     * (THROW 50011), mas, quando ela recusa, o ROLLBACK dela desfaz a transação inteira. Conferir antes em Java dá
     * a mesma mensagem de sempre ao usuário e só chama a procedure quando ela vai dar certo.
     */
    HistoricoStatusOsEntity conferirMudanca(OrdemServicoEntity os, StatusOsEnum novo) {
        HistoricoStatusOsEntity ultimo = historicoStatusOsRepository.findTopByOsOrderByDataHoraDesc(os)
                .orElseThrow(() -> new IllegalStateException("OS sem status no histórico: " + os.getNumero()));
        StatusOsEnum atual = StatusOsEnum.doNome(ultimo.getStatus().getStatus());
        if (atual == novo) {
            throw new RegraNegocioException("A OS já está com este status."); // RN-HOS-04
        }
        if (atual == StatusOsEnum.CONCLUIDA) {
            throw new RegraNegocioException("OS concluída não pode ser reaberta; abra uma nova OS."); // RN-OS-03
        }
        if (!atual.podeMudarPara(novo)) {
            throw new RegraNegocioException("Mudança de status não permitida: de " + atual.getNome()
                    + " para " + novo.getNome() + "."); // RN-HOS-02
        }
        return ultimo;
    }

    /**
     * Muda o status do item conferindo RN-HIT-04 (não repete o atual) e RN-HIT-02 (só transições permitidas).
     * RN-ITE-26: ao passar a 'Executada', e só então, o item recebe data_finalizacao (hora do servidor).
     * ALTERADO (18/11/2026) [E3-09]: a data_finalizacao passou a ser preenchida pelo trigger
     * trg_historicos_status_itens_sincronizar_item_e_os (ver o comentário no fim do método).
     */
    void mudarStatus(ItemEntity item, StatusItemEnum novo) {
        HistoricoStatusItemEntity ultimo = historicoStatusItemRepository.findTopByItemOrderByDataHoraDesc(item)
                .orElseThrow(() -> new IllegalStateException("Item sem status no histórico: " + item.getCodigo()));
        StatusItemEnum atual = StatusItemEnum.doNome(ultimo.getStatusItem().getStatus());
        if (atual == novo) {
            throw new RegraNegocioException("O item já está com este status."); // RN-HIT-04
        }
        if (!atual.podeMudarPara(novo)) {
            throw new RegraNegocioException("Mudança de status não permitida: de " + atual.getNome()
                    + " para " + novo.getNome() + "."); // RN-HIT-02
        }
        // REMOVIDO (18/11/2026) [E3-09]: o bloco "if (novo == EXECUTADA) { item.setDataFinalizacao(agora ou
        // data_entrada); itemRepository.save(item); }". Saiu porque o trigger
        // trg_historicos_status_itens_sincronizar_item_e_os faz exatamente isso no banco quando o status 'Executada'
        // entra no histórico: usa a data_hora do histórico e, se ela for anterior à data_entrada, usa a data_entrada
        // (RN-ITE-25 e CHECK ck_itens_datas_fim). Fazer nos dois lugares gravaria duas datas diferentes (a do Java e a do
        // trigger); por isso a coluna também ficou "só leitura" na ItemEntity (E3-07).
        gravar(item, novo, proximaDataHora(ultimo.getDataHora()));
        if (novo == StatusItemEnum.EXECUTADA) {
            // INCLUÍDO (18/11/2026) [E3-09]: flush envia o INSERT do histórico agora (o trigger roda e grava
            // data_finalizacao) e refresh relê o item, para a resposta da API já trazer a data preenchida pelo banco.
            entityManager.flush();
            entityManager.refresh(item);
        }
    }

    /**
     * Aba "Fluxo de status": Pendente -> Aguardando execução é automático quando a rota do item está definida e os
     * dados obrigatórios estão preenchidos. Interpretação usada aqui: o item tem pelo menos uma etapa (RN-ITE-03)
     * e, se a rota tem Pintura, tem a cor pedida e a tinta definida (RN-ITE-05 e RN-ITE-11).
     *
     * @return true quando o item passou a 'Aguardando execução' nesta chamada.
     */
    boolean avaliarProntidao(ItemEntity item) {
        if (statusAtual(item) != StatusItemEnum.PENDENTE) {
            return false;
        }
        List<ItemRotaProcessoEntity> etapas = itemRotaProcessoRepository.findByItemOrderByOrdem(item);
        if (etapas.isEmpty()) {
            return false;
        }
        boolean temPintura = etapas.stream().anyMatch(e -> RotaProcessoService.ehPintura(e.getRotaProcesso()));
        boolean pronto = !temPintura || (item.getCor() != null && item.getTinta() != null);
        if (pronto) {
            mudarStatus(item, StatusItemEnum.AGUARDANDO_EXECUCAO);
        }
        return pronto;
    }

    // ------------------------------------------------------------------ gravação do histórico

    private void gravar(OrdemServicoEntity os, StatusOsEnum status, LocalDateTime dataHora) {
        HistoricoStatusOsEntity h = new HistoricoStatusOsEntity();
        h.setOs(os);
        h.setStatus(entidade(status));
        h.setDataHora(dataHora);
        historicoStatusOsRepository.save(h);
    }

    private void gravar(ItemEntity item, StatusItemEnum status, LocalDateTime dataHora) {
        HistoricoStatusItemEntity h = new HistoricoStatusItemEntity();
        h.setItem(item);
        h.setStatusItem(entidade(status));
        h.setDataHora(dataHora);
        historicoStatusItemRepository.save(h);
    }

    /**
     * RN-SIS-09 e RN-HOS-07 / RN-HIT-07 (D2 do cronograma): data_hora é datetime e faz parte da chave do histórico.
     * O Service grava a hora do servidor truncada em segundos (valor exato num datetime), então a chave do objeto é a
     * mesma gravada no banco. Se a última mudança da mesma OS ou do mesmo item foi no mesmo segundo, soma 1 segundo:
     * assim a chave não se repete e "o registro mais recente" (RN-HOS-03, RN-HIT-03) é sempre um só.
     */
    static LocalDateTime proximaDataHora(LocalDateTime ultima) {
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (ultima != null && !agora.isAfter(ultima)) {
            return ultima.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        }
        return agora;
    }
}
