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
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.StatusItemRepository;
import br.fatec.projetoextensao.repositories.StatusOsRepository;
import br.fatec.projetoextensao.security.Perfis;
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
 */
@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusOsRepository statusOsRepository;
    private final StatusItemRepository statusItemRepository;
    private final HistoricoStatusOsRepository historicoStatusOsRepository;
    private final HistoricoStatusItemRepository historicoStatusItemRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    private final ItemRepository itemRepository;

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
        gravar(os, novo, proximaDataHora(ultimo.getDataHora()));
    }

    /**
     * Muda o status do item conferindo RN-HIT-04 (não repete o atual) e RN-HIT-02 (só transições permitidas).
     * RN-ITE-26: ao passar a 'Executada', e só então, o item recebe data_finalizacao (hora do servidor).
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
        if (novo == StatusItemEnum.EXECUTADA) {
            LocalDateTime agora = LocalDateTime.now();
            // RN-ITE-25 e CHECK ck_itens_datas_fim: data_finalizacao >= data_entrada (preenchida pelo sistema).
            item.setDataFinalizacao(agora.isBefore(item.getDataEntrada()) ? item.getDataEntrada() : agora);
            itemRepository.save(item);
        }
        gravar(item, novo, proximaDataHora(ultimo.getDataHora()));
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
