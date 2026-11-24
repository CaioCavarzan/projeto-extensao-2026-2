package br.fatec.projetoextensao.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;
import br.fatec.projetoextensao.entities.RotaProcessoEntity;
import br.fatec.projetoextensao.entities.StatusItemEntity;
import br.fatec.projetoextensao.entities.StatusOsEntity;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.repositories.FonteAquisicaoRepository;
import br.fatec.projetoextensao.repositories.RotaProcessoRepository;
import br.fatec.projetoextensao.repositories.StatusItemRepository;
import br.fatec.projetoextensao.repositories.StatusOsRepository;
import lombok.RequiredArgsConstructor;

/**
 * Ao iniciar a aplicação, confere se as listas fixas que o Java usa pelo nome existem no
 * Com a carga inicial de 14/10 já rodada, nada é inserido. Num banco vazio (ou no banco de teste), as listas
 * passam a existir e as regras de status funcionam desde o primeiro uso.
 */

@Component
@RequiredArgsConstructor
public class VerificacaoListasFixas {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoListasFixas.class);

    private final StatusOsRepository statusOsRepository;
    private final StatusItemRepository statusItemRepository;
    private final FonteAquisicaoRepository fonteAquisicaoRepository;
    private final RotaProcessoRepository rotaProcessoRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void conferir() {
        for (StatusOsEnum s : StatusOsEnum.values()) {
            if (statusOsRepository.findByStatus(s.getNome()).isEmpty()) {
                StatusOsEntity novo = new StatusOsEntity();
                novo.setStatus(s.getNome());
                statusOsRepository.save(novo);
                log.warn("Status da OS '{}' não existia no banco e foi cadastrado (RN-SOS-01).", s.getNome());
            }
        }
        for (StatusItemEnum s : StatusItemEnum.values()) {
            if (statusItemRepository.findByStatus(s.getNome()).isEmpty()) {
                StatusItemEntity novo = new StatusItemEntity();
                novo.setStatus(s.getNome());
                statusItemRepository.save(novo);
                log.warn("Status do item '{}' não existia no banco e foi cadastrado (RN-SIT-01).", s.getNome());
            }
        }
        if (fonteAquisicaoRepository.findByDescricaoIgnoreCase(FonteAquisicaoService.NAO_INFORMADA).isEmpty()) {
            FonteAquisicaoEntity fonte = new FonteAquisicaoEntity();
            fonte.setDescricao(FonteAquisicaoService.NAO_INFORMADA);
            fonteAquisicaoRepository.save(fonte);
            log.warn("Fonte '{}' não existia no banco e foi cadastrada (RN-FON-01).", FonteAquisicaoService.NAO_INFORMADA);
        }
        for (String etapa : new String[] {RotaProcessoService.JATEAMENTO, RotaProcessoService.BANHO,
                RotaProcessoService.PINTURA}) {
            if (rotaProcessoRepository.findByNomeIgnoreCase(etapa).isEmpty()) {
                RotaProcessoEntity rota = new RotaProcessoEntity();
                rota.setNome(etapa);
                rotaProcessoRepository.save(rota);
                log.warn("Etapa '{}' não existia no banco e foi cadastrada (RN-ROT-01).", etapa);
            }
        }
    }
}
