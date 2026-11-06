package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.CronogramaAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaRequest;
import br.fatec.projetoextensao.dtos.CronogramaEtapaResponse;
import br.fatec.projetoextensao.dtos.CronogramaRequest;
import br.fatec.projetoextensao.dtos.CronogramaResponse;
import br.fatec.projetoextensao.entities.CronogramaEntity;
import br.fatec.projetoextensao.entities.CronogramaItemEntity;
import br.fatec.projetoextensao.entities.CronogramaItemId;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.CronogramaItemRepository;
import br.fatec.projetoextensao.repositories.CronogramaRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): cronograma de produção (tabelas Cronogramas e CronogramasItens).
 * Regras: RN-CRO-01 a RN-CRO-07, RN-CRI-01 a RN-CRI-09, RN-SIS-04 (cronograma e etapas na mesma transação) e
 * RN-SIS-08 (usuário que registrou = usuário logado).
 */
@Service
@RequiredArgsConstructor
public class CronogramaService {

    private final CronogramaRepository cronogramaRepository;
    private final CronogramaItemRepository cronogramaItemRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    private final StatusService statusService;
    private final UsuarioService usuarioService;

    /**
     * RN-CRO-01: só é gravado com pelo menos uma etapa. RN-CRO-02: um cronograma por dia. RN-CRO-05/06: data
     * obrigatória e não anterior a hoje. RN-CRO-04: o usuário que registra é o logado. A fila segue a ordem enviada
     * (posição 0, 1, 2...; RN-CRI-06/07); sugerirFila reordena pela pontuação (RN-CRI-09).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse criar(CronogramaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe a data planejada e as etapas do cronograma.");
            v.verificar();
        }
        validarData(v, dto.dataPlanejada());
        String observacao = v.opcional(dto.observacao(), "Observação", 255); // RN-CRO-07
        v.exigir(dto.etapas() != null && !dto.etapas().isEmpty(), "Inclua pelo menos uma etapa no cronograma."); // RN-CRO-01
        v.verificar();
        if (cronogramaRepository.existsByDataPlanejada(dto.dataPlanejada())) {
            throw RegraNegocioException.conflito("Já existe cronograma para esta data."); // RN-CRO-02
        }

        CronogramaEntity c = new CronogramaEntity();
        c.setUsuarioCadastrou(usuarioService.usuarioLogadoEntidade()); // RN-CRO-04 e RN-SIS-08
        c.setDataPlanejada(dto.dataPlanejada());
        c.setObservacao(observacao);
        c = cronogramaRepository.save(c);
        int posicao = 0;
        for (CronogramaEtapaRequest etapa : dto.etapas()) {
            incluirNaFila(c, etapa, posicao++);
        }
        return paraResponse(c);
    }

    /** Altera data e observação. RN-CRO-06: cronograma de dia passado não é alterado; a nova data não é passada. */
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
            throw RegraNegocioException.conflito("Já existe cronograma para esta data."); // RN-CRO-02
        }
        c.setDataPlanejada(dto.dataPlanejada());
        c.setObservacao(observacao);
        return paraResponse(cronogramaRepository.save(c));
    }

    /** Inclui uma etapa no fim da fila (RN-CRI-01 a RN-CRI-08). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse incluirEtapa(int codigo, CronogramaEtapaRequest dto) {
        CronogramaEntity c = buscarEditavel(codigo);
        int posicao = cronogramaItemRepository.findByCronogramaOrderByPosicao(c).size();
        incluirNaFila(c, dto, posicao);
        return paraResponse(c);
    }

    /**
     * Tira uma etapa da fila e renumera as seguintes (RN-CRI-07). RN-CRO-01: o cronograma não fica vazio
     * (para isso, exclua o cronograma).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public CronogramaResponse removerEtapa(int codigo, int etapaCodigo) {
        CronogramaEntity c = buscarEditavel(codigo);
        CronogramaItemEntity ci = cronogramaItemRepository.findById(new CronogramaItemId(codigo, etapaCodigo))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa não está neste cronograma."));
        List<CronogramaItemEntity> fila = cronogramaItemRepository.findByCronogramaOrderByPosicao(c);
        if (fila.size() <= 1) {
            throw new RegraNegocioException("O cronograma precisa de pelo menos uma etapa; exclua o cronograma."); // RN-CRO-01
        }
        cronogramaItemRepository.delete(ci);
        cronogramaItemRepository.flush();
        renumerar(cronogramaItemRepository.findByCronogramaOrderByPosicao(c)); // RN-CRI-07
        return paraResponse(c);
    }

    /** RN-CRI-07: a fila recebe a nova ordem e é renumerada 0, 1, 2... (todas as etapas, cada uma uma vez). */
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

    /**
     * RN-CRI-09: fila sugerida pela pontuação (maior primeiro) e, no empate, pelo prazo de entrega (o mais próximo
     * primeiro). Etapas sem pontuação ou sem prazo vão para o fim.
     */
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

    /** Exclui o cronograma e a sua fila. RN-CRO-06: dia passado não é alterado. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public void excluir(int codigo) {
        CronogramaEntity c = buscarEditavel(codigo);
        cronogramaItemRepository.deleteAll(cronogramaItemRepository.findByCronogramaOrderByPosicao(c));
        cronogramaItemRepository.flush();
        cronogramaRepository.delete(c);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public CronogramaResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo));
    }

    /** Cronograma do dia (ou vazio, se não houver). */
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

    /**
     * Uma etapa na fila: RN-CRI-03 (a mesma etapa não entra duas vezes), RN-CRI-01 (etapa não concluída de item não
     * encerrado), RN-CRI-04 (quantidade > 0, 2 casas), RN-CRI-05 (não passa do que falta planejar),
     * RN-CRI-06 (posição >= 0) e RN-CRI-08/09 (pontuação = score do item no momento).
     */
    private void incluirNaFila(CronogramaEntity c, CronogramaEtapaRequest dto, int posicao) {
        if (dto == null || dto.etapaCodigo() == null) {
            throw new RegraNegocioException("Informe a etapa do item.");
        }
        ItemRotaProcessoEntity etapa = itemRotaProcessoRepository.findById(dto.etapaCodigo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa do item não encontrada."));
        if (cronogramaItemRepository.existsById(new CronogramaItemId(c.getCodigo(), etapa.getCodigo()))) {
            throw RegraNegocioException.conflito("Esta etapa já está no cronograma."); // RN-CRI-03
        }
        ItemEntity item = etapa.getItem();
        if (etapa.getDataConclusao() != null || statusService.statusAtual(item).isEncerrado()) {
            throw new RegraNegocioException("Esta etapa já foi concluída ou o item está encerrado."); // RN-CRI-01
        }
        BigDecimal qtd = dto.qtdPlanejado();
        if (qtd == null || qtd.signum() <= 0 || !Valores.casasValidas(qtd, Valores.DIGITOS_DECIMAL_10_2)) {
            throw new RegraNegocioException("Quantidade planejada deve ser maior que zero."); // RN-CRI-04
        }
        BigDecimal pendente = item.getQtdTotal().subtract(cronogramaItemRepository.somarPlanejado(etapa));
        if (qtd.compareTo(pendente) > 0) {
            throw new RegraNegocioException("Quantidade planejada maior que a pendente ("
                    + Valores.normalizar(pendente.max(BigDecimal.ZERO)).toPlainString() + ")."); // RN-CRI-05
        }
        CronogramaItemEntity ci = new CronogramaItemEntity();
        ci.setCronograma(c);
        ci.setItemRotaProcesso(etapa);
        ci.setQtdPlanejado(Valores.normalizar(qtd));
        ci.setPosicao(posicao); // RN-CRI-06
        ci.setPontuacao(item.getScorePrioridade()); // RN-CRI-08 e RN-CRI-09
        cronogramaItemRepository.save(ci);
    }

    /** RN-CRI-07: posições 0, 1, 2... na ordem da lista. */
    private void renumerar(List<CronogramaItemEntity> fila) {
        for (int i = 0; i < fila.size(); i++) {
            CronogramaItemEntity ci = fila.get(i);
            if (ci.getPosicao() != i) {
                ci.setPosicao(i);
                cronogramaItemRepository.save(ci);
            }
        }
    }

    /** RN-CRO-05 e RN-CRO-06: data obrigatória e não anterior a hoje. */
    private void validarData(Validacao v, LocalDate data) {
        if (data == null) {
            v.erro("Informe a data planejada."); // RN-CRO-05
        } else if (data.isBefore(LocalDate.now())) {
            v.erro("Não é possível planejar ou alterar um dia que já passou."); // RN-CRO-06
        }
    }

    /** RN-CRO-06: cronograma de dia passado não é alterado. */
    private CronogramaEntity buscarEditavel(int codigo) {
        CronogramaEntity c = buscarEntidade(codigo);
        if (c.getDataPlanejada().isBefore(LocalDate.now())) {
            throw new RegraNegocioException("Não é possível planejar ou alterar um dia que já passou.");
        }
        return c;
    }

    private CronogramaEntity buscarEntidade(int codigo) {
        return cronogramaRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cronograma não encontrado.")); // RN-CRI-02
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
