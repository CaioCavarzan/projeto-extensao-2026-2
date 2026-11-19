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
 * Status da OS e do item.
 */

@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusOsRepository statusOsRepository;
    private final StatusItemRepository statusItemRepository;
    private final HistoricoStatusOsRepository historicoStatusOsRepository;
    private final HistoricoStatusItemRepository historicoStatusItemRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    private final EntityManager entityManager;

    // ------------------------------------------------------------------ listas (só leitura)

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listarStatusOs() {
        return statusOsRepository.findAllByOrderByCodigo().stream()
                .map(s -> new ItemListaResponse(s.getCodigo(), s.getStatus()))
                .toList();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listarStatusItens() {
        return statusItemRepository.findAllByOrderByCodigo().stream()
                .map(s -> new ItemListaResponse(s.getCodigo(), s.getStatus()))
                .toList();
    }

    // ------------------------------------------------------------------ uso interno dos outros Services

    StatusOsEntity entidade(StatusOsEnum status) {
        return statusOsRepository.findByStatus(status.getNome())
                .orElseThrow(() -> new IllegalStateException("Status da OS não cadastrado: " + status.getNome()));
    }

    StatusItemEntity entidade(StatusItemEnum status) {
        return statusItemRepository.findByStatus(status.getNome())
                .orElseThrow(() -> new IllegalStateException("Status do item não cadastrado: " + status.getNome()));
    }

    StatusOsEnum statusAtual(OrdemServicoEntity os) {
        return historicoStatusOsRepository.findTopByOsOrderByDataHoraDesc(os)
                .map(h -> StatusOsEnum.doNome(h.getStatus().getStatus()))
                .orElseThrow(() -> new IllegalStateException("OS sem status no histórico: " + os.getNumero()));
    }

    StatusItemEnum statusAtual(ItemEntity item) {
        return historicoStatusItemRepository.findTopByItemOrderByDataHoraDesc(item)
                .map(h -> StatusItemEnum.doNome(h.getStatusItem().getStatus()))
                .orElseThrow(() -> new IllegalStateException("Item sem status no histórico: " + item.getCodigo()));
    }

    void registrarInicial(OrdemServicoEntity os) {
        gravar(os, StatusOsEnum.CRIADA, proximaDataHora(null));
    }

    void registrarInicial(ItemEntity item) {
        gravar(item, StatusItemEnum.PENDENTE, proximaDataHora(null));
    }

    void mudarStatus(OrdemServicoEntity os, StatusOsEnum novo) {
        HistoricoStatusOsEntity ultimo = conferirMudanca(os, novo);
        gravar(os, novo, proximaDataHora(ultimo.getDataHora()));
    }

    HistoricoStatusOsEntity conferirMudanca(OrdemServicoEntity os, StatusOsEnum novo) {
        HistoricoStatusOsEntity ultimo = historicoStatusOsRepository.findTopByOsOrderByDataHoraDesc(os)
                .orElseThrow(() -> new IllegalStateException("OS sem status no histórico: " + os.getNumero()));
        StatusOsEnum atual = StatusOsEnum.doNome(ultimo.getStatus().getStatus());
        if (atual == novo) {
            throw new RegraNegocioException("A OS já está com este status."); 
        }
        if (atual == StatusOsEnum.CONCLUIDA) {
            throw new RegraNegocioException("OS concluída não pode ser reaberta; abra uma nova OS."); 
        }
        if (!atual.podeMudarPara(novo)) {
            throw new RegraNegocioException("Mudança de status não permitida: de " + atual.getNome()
                    + " para " + novo.getNome() + "."); 
        }
        return ultimo;
    }

    void mudarStatus(ItemEntity item, StatusItemEnum novo) {
        HistoricoStatusItemEntity ultimo = historicoStatusItemRepository.findTopByItemOrderByDataHoraDesc(item)
                .orElseThrow(() -> new IllegalStateException("Item sem status no histórico: " + item.getCodigo()));
        StatusItemEnum atual = StatusItemEnum.doNome(ultimo.getStatusItem().getStatus());
        if (atual == novo) {
            throw new RegraNegocioException("O item já está com este status."); 
        }
        if (!atual.podeMudarPara(novo)) {
            throw new RegraNegocioException("Mudança de status não permitida: de " + atual.getNome()
                    + " para " + novo.getNome() + "."); 
        }
        gravar(item, novo, proximaDataHora(ultimo.getDataHora()));
        if (novo == StatusItemEnum.EXECUTADA) {
            entityManager.flush();
            entityManager.refresh(item);
        }
    }

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

    static LocalDateTime proximaDataHora(LocalDateTime ultima) {
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (ultima != null && !agora.isAfter(ultima)) {
            return ultima.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        }
        return agora;
    }
}
