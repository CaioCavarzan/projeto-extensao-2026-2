package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.CronogramaAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaResponse;
import br.fatec.projetoextensao.dtos.CronogramaRequest;
import br.fatec.projetoextensao.dtos.CronogramaResponse;
import br.fatec.projetoextensao.dtos.ReplanejamentoRequest;
import br.fatec.projetoextensao.dtos.ReplanejamentoResponse;
import br.fatec.projetoextensao.entities.CronogramaEntity;
import br.fatec.projetoextensao.entities.CronogramaItemEntity;
import br.fatec.projetoextensao.entities.CronogramaItemId;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.exceptions.ErrosBanco;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.CronogramaItemRepository;
import br.fatec.projetoextensao.repositories.CronogramaRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.ProcedimentosBanco;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * Cronograma de produção (tabelas Cronogramas e CronogramasItens).
 */

@Service
@RequiredArgsConstructor
public class CronogramaService {

    private static final Logger log = LoggerFactory.getLogger(CronogramaService.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CronogramaRepository cronogramaRepository;
    private final CronogramaItemRepository cronogramaItemRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    private final StatusService statusService;
    private final UsuarioService usuarioService;
    // INCLUÍDO (18/11/2026) [E3-14]: a procedure de replanejamento.
    private final ProcedimentosBanco procedimentosBanco;

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse criar(CronogramaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe a data planejada e as etapas do cronograma.");
            v.verificar();
        }
        validarData(v, dto.dataPlanejada());
        String observacao = v.opcional(dto.observacao(), "Observação", 255); 
        v.exigir(dto.etapas() != null && !dto.etapas().isEmpty(), "Inclua pelo menos uma etapa no cronograma."); 
        v.verificar();
        if (cronogramaRepository.existsByDataPlanejada(dto.dataPlanejada())) {
            throw RegraNegocioException.conflito("Já existe cronograma para esta data."); 
        }

        CronogramaEntity c = new CronogramaEntity();
        c.setUsuarioCadastrou(usuarioService.usuarioLogadoEntidade()); 
        c.setDataPlanejada(dto.dataPlanejada());
        c.setObservacao(observacao);
        c = cronogramaRepository.save(c);
        int posicao = 0;
        for (CronogramaEtapaRequest etapa : dto.etapas()) {
            incluirNaFila(c, etapa, posicao++);
        }
        return paraResponse(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse atualizar(int codigo, CronogramaAtualizacaoRequest dto) {
        CronogramaEntity c = buscarEditavel(codigo);
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe a data planejada.");
            v.verificar();
        }
        validarData(v, dto.dataPlanejada());
        String observacao = v.opcional(dto.observacao(), "Observação", 255);
        v.verificar();
        if (cronogramaRepository.existsByDataPlanejadaAndCodigoNot(dto.dataPlanejada(), codigo)) {
            throw RegraNegocioException.conflito("Já existe cronograma para esta data."); 
        }
        c.setDataPlanejada(dto.dataPlanejada());
        c.setObservacao(observacao);
        return paraResponse(cronogramaRepository.save(c));
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse incluirEtapa(int codigo, CronogramaEtapaRequest dto) {
        CronogramaEntity c = buscarEditavel(codigo);
        int posicao = cronogramaItemRepository.findByCronogramaOrderByPosicao(c).size();
        incluirNaFila(c, dto, posicao);
        return paraResponse(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse removerEtapa(int codigo, int etapaCodigo) {
        CronogramaEntity c = buscarEditavel(codigo);
        CronogramaItemEntity ci = cronogramaItemRepository.findById(new CronogramaItemId(codigo, etapaCodigo))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa não está neste cronograma."));
        List<CronogramaItemEntity> fila = cronogramaItemRepository.findByCronogramaOrderByPosicao(c);
        if (fila.size() <= 1) {
            throw new RegraNegocioException("O cronograma precisa de pelo menos uma etapa; exclua o cronograma."); 
        }
        cronogramaItemRepository.delete(ci);
        cronogramaItemRepository.flush();
        renumerar(cronogramaItemRepository.findByCronogramaOrderByPosicao(c)); 
        return paraResponse(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse reordenar(int codigo, List<Integer> etapasNaOrdem) {
        CronogramaEntity c = buscarEditavel(codigo);
        List<CronogramaItemEntity> fila = cronogramaItemRepository.findByCronogramaOrderByPosicao(c);
        Set<Integer> atuais = new HashSet<>();
        fila.forEach(ci -> atuais.add(ci.getItemRotaProcesso().getCodigo()));
        if (etapasNaOrdem == null || etapasNaOrdem.size() != fila.size() || !atuais.equals(new HashSet<>(etapasNaOrdem))) {
            throw new RegraNegocioException("Informe todas as etapas do cronograma, cada uma uma vez.");
        }
        List<CronogramaItemEntity> nova = new ArrayList<>();
        for (Integer etapa : etapasNaOrdem) {
            fila.stream().filter(ci -> ci.getItemRotaProcesso().getCodigo() == etapa).findFirst().ifPresent(nova::add);
        }
        renumerar(nova);
        return paraResponse(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse sugerirFila(int codigo) {
        CronogramaEntity c = buscarEditavel(codigo);
        List<CronogramaItemEntity> fila = new ArrayList<>(cronogramaItemRepository.findByCronogramaOrderByPosicao(c));
        fila.sort(Comparator
                .comparing(CronogramaItemEntity::getPontuacao, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ci -> ci.getItemRotaProcesso().getItem().getPrazoEntrega(),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        renumerar(fila);
        return paraResponse(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public void excluir(int codigo) {
        CronogramaEntity c = buscarEditavel(codigo);
        cronogramaItemRepository.deleteAll(cronogramaItemRepository.findByCronogramaOrderByPosicao(c));
        cronogramaItemRepository.flush();
        cronogramaRepository.delete(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ReplanejamentoResponse replanejar(ReplanejamentoRequest dto) {
        LocalDate origem = dto == null ? null : dto.dataOrigem();
        LocalDate destino = dto == null ? null : dto.dataDestino();
        Validacao v = new Validacao();
        if (origem == null || destino == null) {
            v.erro("Informe o dia de origem e o dia de destino.");
        } else {
            if (!destino.isAfter(origem)) {
                v.erro("A data de destino deve ser posterior à data de origem."); 
            }
            if (destino.isBefore(LocalDate.now())) {
                v.erro("Não é possível planejar ou alterar um dia que já passou."); 
            }
        }
        v.verificar();
        UsuarioEntity usuario = usuarioService.usuarioLogadoEntidade(); 

        Optional<CronogramaEntity> cronogramaOrigem = cronogramaRepository.findByDataPlanejada(origem);
        Optional<CronogramaEntity> cronogramaDestino = cronogramaRepository.findByDataPlanejada(destino);
        long pendentes = cronogramaOrigem.map(c -> contarReplanejaveis(c, cronogramaDestino.orElse(null))).orElse(0L);
        List<String> avisos = new ArrayList<>();
        if (pendentes == 0) {
            avisos.add(cronogramaOrigem.isEmpty()
                    ? "Não há cronograma em " + origem.format(DATA_BR) + ": nada para replanejar."
                    : "Nenhuma etapa pendente em " + origem.format(DATA_BR) + " para levar a " + destino.format(DATA_BR)
                            + " (as etapas foram concluídas, são de itens/OS encerrados ou já estão no destino).");
            return new ReplanejamentoResponse(origem, destino, 0, cronogramaDestino.map(this::paraResponse).orElse(null),
                    avisos);
        }

        int movidas;
        try {
            movidas = procedimentosBanco.replanejarCronograma(origem, destino, usuario.getFuncionarioCodigo());
        } catch (DataAccessException e) {
            throw ErrosBanco.converter(e); 
        }
        if (movidas != pendentes) {
            log.info("Replanejamento de {} para {}: o Java contou {} etapa(s) e a procedure moveu {}.", origem, destino,
                    pendentes, movidas);
        }
        log.info("AUDITORIA login={} acao=REPLANEJAR_CRONOGRAMA origem={} destino={} etapas={}", usuario.getLogin(),
                origem, destino, movidas);
        CronogramaResponse resposta = cronogramaRepository.findByDataPlanejada(destino).map(this::paraResponse).orElse(null);
        avisos.add(movidas + " etapa(s) de " + origem.format(DATA_BR) + " entraram no fim da fila de "
                + destino.format(DATA_BR) + ".");
        if (cronogramaDestino.isEmpty() && resposta != null) {
            avisos.add("O cronograma de " + destino.format(DATA_BR) + " foi criado pelo replanejamento.");
        }
        avisos.add("O cronograma de " + origem.format(DATA_BR) + " continua com as etapas, como registro do que estava planejado.");
        return new ReplanejamentoResponse(origem, destino, movidas, resposta, avisos);
    }

    private long contarReplanejaveis(CronogramaEntity origem, CronogramaEntity destino) {
        Set<Integer> noDestino = destino == null ? Set.of()
                : cronogramaItemRepository.findByCronogramaOrderByPosicao(destino).stream()
                        .map(ci -> ci.getItemRotaProcesso().getCodigo())
                        .collect(Collectors.toSet());
        return cronogramaItemRepository.findByCronogramaOrderByPosicao(origem).stream()
                .map(CronogramaItemEntity::getItemRotaProcesso)
                .filter(e -> e.getDataConclusao() == null)
                .filter(e -> !statusService.statusAtual(e.getItem()).isEncerrado())
                .filter(e -> !statusService.statusAtual(e.getItem().getOs()).isEncerrado())
                .filter(e -> !noDestino.contains(e.getCodigo()))
                .count();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public CronogramaResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo));
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public CronogramaResponse buscarDoDia(LocalDate data) {
        return cronogramaRepository.findByDataPlanejada(data).map(this::paraResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cronograma não encontrado."));
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<CronogramaResponse> listar() {
        return cronogramaRepository.findAllByOrderByDataPlanejadaDesc().stream().map(this::paraResponse).toList();
    }

    // ------------------------------------------------------------------ apoio

    private void incluirNaFila(CronogramaEntity c, CronogramaEtapaRequest dto, int posicao) {
        if (dto == null || dto.etapaCodigo() == null) {
            throw new RegraNegocioException("Informe a etapa do item.");
        }
        ItemRotaProcessoEntity etapa = itemRotaProcessoRepository.findById(dto.etapaCodigo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa do item não encontrada."));
        if (cronogramaItemRepository.existsById(new CronogramaItemId(c.getCodigo(), etapa.getCodigo()))) {
            throw RegraNegocioException.conflito("Esta etapa já está no cronograma."); 
        }
        ItemEntity item = etapa.getItem();
        if (etapa.getDataConclusao() != null || statusService.statusAtual(item).isEncerrado()) {
            throw new RegraNegocioException("Esta etapa já foi concluída ou o item está encerrado."); 
        }
        BigDecimal qtd = dto.qtdPlanejado();
        if (qtd == null || qtd.signum() <= 0 || !Valores.casasValidas(qtd, Valores.DIGITOS_DECIMAL_10_2)) {
            throw new RegraNegocioException("Quantidade planejada deve ser maior que zero."); 
        }
        BigDecimal pendente = item.getQtdTotal().subtract(cronogramaItemRepository.somarPlanejado(etapa));
        if (qtd.compareTo(pendente) > 0) {
            throw new RegraNegocioException("Quantidade planejada maior que a pendente ("
                    + Valores.normalizar(pendente.max(BigDecimal.ZERO)).toPlainString() + ")."); 
        }
        CronogramaItemEntity ci = new CronogramaItemEntity();
        ci.setCronograma(c);
        ci.setItemRotaProcesso(etapa);
        ci.setQtdPlanejado(Valores.normalizar(qtd));
        ci.setPosicao(posicao); // RN-CRI-06
        ci.setPontuacao(item.getScorePrioridade()); 
        cronogramaItemRepository.save(ci);
    }

    private void renumerar(List<CronogramaItemEntity> fila) {
        for (int i = 0; i < fila.size(); i++) {
            CronogramaItemEntity ci = fila.get(i);
            if (ci.getPosicao() != i) {
                ci.setPosicao(i);
                cronogramaItemRepository.save(ci);
            }
        }
    }

    private void validarData(Validacao v, LocalDate data) {
        if (data == null) {
            v.erro("Informe a data planejada."); 
        } else if (data.isBefore(LocalDate.now())) {
            v.erro("Não é possível planejar ou alterar um dia que já passou."); 
        }
    }

    private CronogramaEntity buscarEditavel(int codigo) {
        CronogramaEntity c = buscarEntidade(codigo);
        if (c.getDataPlanejada().isBefore(LocalDate.now())) {
            throw new RegraNegocioException("Não é possível planejar ou alterar um dia que já passou.");
        }
        return c;
    }

    private CronogramaEntity buscarEntidade(int codigo) {
        return cronogramaRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cronograma não encontrado.")); 
    }

    private CronogramaResponse paraResponse(CronogramaEntity c) {
        List<CronogramaEtapaResponse> fila = cronogramaItemRepository.findByCronogramaOrderByPosicao(c).stream()
                .map(ci -> {
                    ItemRotaProcessoEntity e = ci.getItemRotaProcesso();
                    ItemEntity i = e.getItem();
                    return new CronogramaEtapaResponse(e.getCodigo(), i.getCodigo(), i.getDescricao(),
                            i.getOs().getNumero(), e.getRotaProcesso().getNome(), e.getOrdem(), ci.getQtdPlanejado(),
                            ci.getPosicao(), ci.getPontuacao(), i.getPrazoEntrega());
                })
                .toList();
        return new CronogramaResponse(c.getCodigo(), c.getDataPlanejada(), c.getObservacao(),
                c.getUsuarioCadastrou().getLogin(), fila);
    }
}
