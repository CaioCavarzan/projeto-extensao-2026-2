package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.CorrecaoEtapaRequest;
import br.fatec.projetoextensao.dtos.EtapaFilaResponse;
import br.fatec.projetoextensao.dtos.EtapaResponse;
import br.fatec.projetoextensao.dtos.IndicadorRetrabalhoResponse;
import br.fatec.projetoextensao.dtos.IniciarEtapaRequest;
import br.fatec.projetoextensao.dtos.ItemRetrabalhoResponse;
import br.fatec.projetoextensao.entities.FuncionarioEntity;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.RotaProcessoEntity;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.CronogramaItemRepository;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.security.Perfis;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): rota do item e execução das etapas (tabela ItensRotasProcessos).
 * Regras: RN-IRP-01 a RN-IRP-16, RN-ITE-03 (pelo menos uma etapa), RN-ITE-05 (Pintura exige cor), RN-ITE-06
 * (retrabalho não muda o status do item), RN-OS-06 (1ª etapa iniciada leva a OS a 'Em produção') e RN-OS-07.
 *
 * Decisões de implementação (não estão escritas na planilha, mas decorrem dos status do item e da OS):
 * <ul>
 *   <li>etapa só é iniciada quando o item está 'Aguardando execução' (pronto) ou 'Executada' (retrabalho);
 *       item 'Pendente' ainda não está pronto, e item ou OS 'Pausada' não recebe início nem conclusão de etapa;</li>
 *   <li>a Pintura só é iniciada com a tinta definida (sem tinta não há o que aplicar).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ItemRotaProcessoService {

    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    private final ItemRepository itemRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final CronogramaItemRepository cronogramaItemRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final RotaProcessoService rotaProcessoService;
    private final StatusService statusService;

    // ------------------------------------------------------------------ montagem da rota

    /** RN-ITE-03 e RN-IRP-01: etapas iniciais do item, com ordem 1, 2, 3... (chamado ao incluir o item). */
    void criarEtapasIniciais(ItemEntity item, List<RotaProcessoEntity> rotas) {
        int ordem = itemRotaProcessoRepository.maiorOrdem(item);
        for (RotaProcessoEntity rota : rotas) {
            novaEtapa(item, rota, ++ordem);
        }
    }

    /**
     * Acrescenta uma etapa planejada no fim da rota (ordem = maior ordem + 1, RN-IRP-01).
     * RN-ITE-07: item encerrado não muda; RN-OS-07: OS encerrada não muda; RN-ITE-05: Pintura exige cor pedida.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public List<EtapaResponse> adicionarEtapa(int itemCodigo, int rotaCodigo) {
        ItemEntity item = travarItem(itemCodigo);
        garantirOsAberta(item.getOs());
        if (statusService.statusAtual(item).isEncerrado()) {
            throw new RegraNegocioException("Item encerrado não pode ser alterado."); // RN-ITE-07
        }
        RotaProcessoEntity rota = rotaProcessoService.buscarEntidade(rotaCodigo); // RN-IRP-11
        if (RotaProcessoService.ehPintura(rota) && item.getCor() == null) {
            throw new RegraNegocioException("Informe a cor: o item tem pintura."); // RN-ITE-05
        }
        novaEtapa(item, rota, itemRotaProcessoRepository.maiorOrdem(item) + 1);
        statusService.avaliarProntidao(item);
        return etapas(item);
    }

    /**
     * RN-IRP-02 e RN-ITE-06: retrabalho = nova linha com a mesma etapa e a próxima ordem; a etapa concluída nunca é
     * reaberta e o status do item não muda. Reclamação depois da OS encerrada vira OS nova (RN-OS-03, RN-OS-07).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public List<EtapaResponse> registrarRetrabalho(int etapaCodigo) {
        ItemRotaProcessoEntity etapa = buscarEntidade(etapaCodigo);
        ItemEntity item = travarItem(etapa.getItem().getCodigo());
        garantirOsAberta(item.getOs());
        if (statusService.statusAtual(item) == StatusItemEnum.CANCELADA) {
            throw new RegraNegocioException("Item cancelado não recebe retrabalho.");
        }
        if (etapa.getDataConclusao() == null) {
            throw new RegraNegocioException("Só etapa concluída tem retrabalho: esta etapa ainda não foi concluída.");
        }
        novaEtapa(item, etapa.getRotaProcesso(), itemRotaProcessoRepository.maiorOrdem(item) + 1); // RN-IRP-01
        return etapas(item);
    }

    // ------------------------------------------------------------------ execução

    /**
     * Início da etapa: RN-IRP-03 (operador obrigatório), RN-IRP-05 (etapas anteriores concluídas), RN-IRP-07 (hora do
     * servidor), RN-IRP-02 (etapa concluída não reabre) e RN-OS-06 (a OS passa de 'Criada' para 'Em produção').
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public EtapaResponse iniciarEtapa(int etapaCodigo, IniciarEtapaRequest dto) {
        ItemRotaProcessoEntity etapa = buscarEntidade(etapaCodigo);
        ItemEntity item = travarItem(etapa.getItem().getCodigo());
        OrdemServicoEntity os = travarOs(item.getOs());
        StatusOsEnum statusOs = garantirOsEmAndamento(os);
        garantirItemEmExecucao(item, true);
        if (etapa.getDataConclusao() != null) {
            throw new RegraNegocioException("Etapa concluída não pode ser reaberta; registre retrabalho."); // RN-IRP-02
        }
        if (etapa.getDataInicio() != null) {
            throw new RegraNegocioException("Etapa já iniciada.");
        }
        if (dto == null || dto.funcionarioCodigo() == null) {
            throw new RegraNegocioException("Informe o operador da etapa."); // RN-IRP-03 e CHECK do banco
        }
        FuncionarioEntity operador = funcionarioRepository.findById(dto.funcionarioCodigo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionário não encontrado.")); // RN-IRP-16
        boolean anterioresConcluidas = etapasDoItem(item).stream()
                .filter(e -> e.getOrdem() < etapa.getOrdem())
                .allMatch(e -> e.getDataConclusao() != null);
        if (!anterioresConcluidas) {
            throw new RegraNegocioException("Conclua a etapa anterior antes."); // RN-IRP-05
        }
        if (RotaProcessoService.ehPintura(etapa.getRotaProcesso()) && item.getTinta() == null) {
            throw new RegraNegocioException("Defina a tinta do item antes de iniciar a pintura.");
        }
        etapa.setFuncionario(operador);
        etapa.setDataInicio(LocalDateTime.now()); // RN-IRP-07 e RN-IRP-14 (com início, operador já conferido)
        itemRotaProcessoRepository.save(etapa);
        if (statusOs == StatusOsEnum.CRIADA) {
            statusService.mudarStatus(os, StatusOsEnum.EM_PRODUCAO); // RN-OS-06
        }
        return paraResponse(etapa, etapasDoItem(item));
    }

    /**
     * Conclusão da etapa: RN-IRP-04 (precisa estar iniciada; conclusão não antes do início), RN-IRP-07 (hora do
     * servidor) e RN-IRP-06 (concluída a última etapa, o item passa a 'Executada' e recebe data_finalizacao).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public EtapaResponse concluirEtapa(int etapaCodigo) {
        ItemRotaProcessoEntity etapa = buscarEntidade(etapaCodigo);
        ItemEntity item = travarItem(etapa.getItem().getCodigo());
        garantirOsEmAndamento(travarOs(item.getOs()));
        garantirItemEmExecucao(item, false);
        if (etapa.getDataInicio() == null) {
            throw new RegraNegocioException("A etapa precisa ter sido iniciada."); // RN-IRP-04
        }
        if (etapa.getDataConclusao() != null) {
            throw new RegraNegocioException("Etapa já concluída.");
        }
        LocalDateTime agora = LocalDateTime.now();
        etapa.setDataConclusao(agora.isBefore(etapa.getDataInicio()) ? etapa.getDataInicio() : agora); // RN-IRP-04
        itemRotaProcessoRepository.save(etapa);

        List<ItemRotaProcessoEntity> etapas = etapasDoItem(item);
        boolean todasConcluidas = etapas.stream().allMatch(e -> e.getDataConclusao() != null);
        if (todasConcluidas && statusService.statusAtual(item) == StatusItemEnum.AGUARDANDO_EXECUCAO) {
            statusService.mudarStatus(item, StatusItemEnum.EXECUTADA); // RN-IRP-06 e RN-ITE-26
        }
        return paraResponse(etapa, etapas);
    }

    /**
     * RN-IRP-07: correção manual de datas, só por ADMIN. Corrige horários e operador de etapa já iniciada; não
     * inicia, não conclui e não reabre etapa (para isso existem iniciar e concluir; RN-IRP-02).
     * Mantém os CHECK do banco: conclusão só com início e não antes dele; início exige operador.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public EtapaResponse corrigirDatas(int etapaCodigo, CorrecaoEtapaRequest dto) {
        ItemRotaProcessoEntity etapa = buscarEntidade(etapaCodigo);
        if (dto == null) {
            throw new RegraNegocioException("Informe as datas corrigidas.");
        }
        if (etapa.getDataInicio() == null) {
            throw new RegraNegocioException("Etapa ainda não iniciada: use a opção de iniciar.");
        }
        if (dto.dataInicio() == null) {
            throw new RegraNegocioException("Informe a data de início da etapa.");
        }
        boolean concluida = etapa.getDataConclusao() != null;
        if (concluida && dto.dataConclusao() == null) {
            throw new RegraNegocioException("Etapa concluída não pode ser reaberta; registre retrabalho."); // RN-IRP-02
        }
        if (!concluida && dto.dataConclusao() != null) {
            throw new RegraNegocioException("Para concluir a etapa, use a opção de concluir.");
        }
        LocalDateTime agora = LocalDateTime.now();
        if (dto.dataInicio().isAfter(agora) || (dto.dataConclusao() != null && dto.dataConclusao().isAfter(agora))) {
            throw new RegraNegocioException("As datas da etapa não podem estar no futuro.");
        }
        if (dto.dataConclusao() != null && dto.dataConclusao().isBefore(dto.dataInicio())) {
            throw new RegraNegocioException("A conclusão não pode ser antes do início."); // RN-IRP-04, RN-IRP-15
        }
        if (dto.funcionarioCodigo() != null) {
            etapa.setFuncionario(funcionarioRepository.findById(dto.funcionarioCodigo())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionário não encontrado.")));
        }
        etapa.setDataInicio(dto.dataInicio());
        etapa.setDataConclusao(dto.dataConclusao());
        itemRotaProcessoRepository.save(etapa);
        return paraResponse(etapa, etapasDoItem(etapa.getItem()));
    }

    /**
     * RN-IRP-08: só etapa planejada (sem início) sai da rota. RN-ITE-03: o item fica com pelo menos uma etapa.
     * Etapa que está num cronograma precisa sair do cronograma antes (a FK de CronogramasItens impediria).
     * RN-ITE-05: se a rota ficar sem Pintura, o item fica sem cor e sem tinta.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public List<EtapaResponse> removerEtapa(int etapaCodigo) {
        ItemRotaProcessoEntity etapa = buscarEntidade(etapaCodigo);
        ItemEntity item = travarItem(etapa.getItem().getCodigo());
        garantirOsAberta(item.getOs());
        if (etapa.getDataInicio() != null) {
            throw new RegraNegocioException("Etapa já iniciada não pode ser removida."); // RN-IRP-08
        }
        if (cronogramaItemRepository.existsByItemRotaProcesso(etapa)) {
            throw RegraNegocioException.conflito("Etapa planejada em cronograma: retire-a do cronograma antes.");
        }
        if (etapasDoItem(item).size() <= 1) {
            throw new RegraNegocioException("Informe pelo menos uma etapa do processo."); // RN-ITE-03
        }
        itemRotaProcessoRepository.delete(etapa);
        itemRotaProcessoRepository.flush();
        boolean aindaTemPintura = etapasDoItem(item).stream().anyMatch(e -> RotaProcessoService.ehPintura(e.getRotaProcesso()));
        if (!aindaTemPintura && (item.getCor() != null || item.getTinta() != null)) {
            item.setTinta(null); // RN-ITE-05: item sem Pintura fica sem cor e sem tinta
            item.setCor(null);
            itemRepository.save(item);
        }
        statusService.avaliarProntidao(item);
        return etapas(item);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<EtapaResponse> listarDoItem(int itemCodigo) {
        ItemEntity item = itemRepository.findById(itemCodigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado."));
        return etapas(item);
    }

    // ------------------------------------------------------------------ indicador

    /**
     * RN-IRP-09: % de retrabalho = itens com alguma etapa repetida ÷ itens executados no período × 100
     * (indicador do PDCA). Conta os itens finalizados entre {@code inicio} e {@code fim}, inclusive.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public IndicadorRetrabalhoResponse percentualRetrabalho(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new RegraNegocioException("Informe o período: data inicial e data final.");
        }
        List<ItemEntity> executados = itemRepository
                .findByDataFinalizacaoGreaterThanEqualAndDataFinalizacaoLessThan(inicio.atStartOfDay(),
                        fim.plusDays(1).atStartOfDay())
                .stream()
                .filter(i -> statusService.statusAtual(i) == StatusItemEnum.EXECUTADA)
                .toList();
        long comRetrabalho = 0;
        if (!executados.isEmpty()) {
            Map<Integer, List<ItemRotaProcessoEntity>> porItem = itemRotaProcessoRepository.findByItemIn(executados)
                    .stream().collect(Collectors.groupingBy(e -> e.getItem().getCodigo()));
            comRetrabalho = porItem.values().stream().filter(this::temEtapaRepetida).count();
        }
        BigDecimal percentual = executados.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(comRetrabalho * 100).divide(BigDecimal.valueOf(executados.size()), 2, RoundingMode.HALF_UP);
        return new IndicadorRetrabalhoResponse(inicio, fim, executados.size(), comRetrabalho, percentual);
    }

    /**
     * INCLUÍDO (21/10/2026): itens executados no período que tiveram alguma etapa repetida, com o nome das etapas
     * repetidas. São os itens contados no numerador do % de retrabalho (RN-IRP-09); a tela Produção mostra a lista.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemRetrabalhoResponse> itensComRetrabalho(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new RegraNegocioException("Informe o período: data inicial e data final.");
        }
        List<ItemEntity> executados = itemRepository
                .findByDataFinalizacaoGreaterThanEqualAndDataFinalizacaoLessThan(inicio.atStartOfDay(),
                        fim.plusDays(1).atStartOfDay())
                .stream()
                .filter(i -> statusService.statusAtual(i) == StatusItemEnum.EXECUTADA)
                .toList();
        if (executados.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<ItemRotaProcessoEntity>> porItem = itemRotaProcessoRepository.findByItemIn(executados)
                .stream().collect(Collectors.groupingBy(e -> e.getItem().getCodigo()));
        List<ItemRetrabalhoResponse> resposta = new ArrayList<>();
        for (ItemEntity item : executados) {
            List<ItemRotaProcessoEntity> rota = new ArrayList<>(porItem.getOrDefault(item.getCodigo(), List.of()));
            rota.sort(Comparator.comparingInt(ItemRotaProcessoEntity::getOrdem));
            Set<Integer> vistas = new HashSet<>();
            Set<String> repetidas = new LinkedHashSet<>();
            for (ItemRotaProcessoEntity e : rota) {
                if (!vistas.add(e.getRotaProcesso().getCodigo())) {
                    repetidas.add(e.getRotaProcesso().getNome());
                }
            }
            if (!repetidas.isEmpty()) {
                resposta.add(new ItemRetrabalhoResponse(item.getCodigo(), item.getDescricao(), item.getOs().getNumero(),
                        item.getOs().getCliente().getPessoa().getNome(), item.getDataFinalizacao(), List.copyOf(repetidas)));
            }
        }
        resposta.sort(Comparator.comparing(ItemRetrabalhoResponse::dataFinalizacao,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return resposta;
    }

    // ------------------------------------------------------------------ fila de produção

    /**
     * INCLUÍDO (21/10/2026): fila de produção, usada pela tela Produção e na montagem do cronograma.
     * São as etapas ainda não concluídas de itens que não foram cancelados, em OS que não está encerrada; entram
     * também as etapas de retrabalho de itens já executados (RN-IRP-02 e RN-ITE-06). Ordem: prazo de entrega mais
     * próximo primeiro (sem prazo no fim), depois OS, item e ordem da etapa.
     * podeIniciar e motivo repetem, só como orientação para a tela, as conferências de iniciarEtapa (OS ou item
     * pausados, item pendente, RN-IRP-05 e Pintura sem tinta); quem decide continua sendo iniciarEtapa.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<EtapaFilaResponse> listarFila() {
        List<ItemRotaProcessoEntity> abertas = itemRotaProcessoRepository.findByDataConclusaoIsNull();
        Map<Integer, ItemEntity> itens = new LinkedHashMap<>();
        abertas.forEach(e -> itens.putIfAbsent(e.getItem().getCodigo(), e.getItem()));
        if (itens.isEmpty()) {
            return List.of();
        }
        Map<Integer, StatusItemEnum> statusItem = new HashMap<>();
        Map<Integer, StatusOsEnum> statusOs = new HashMap<>();
        for (ItemEntity i : itens.values()) {
            statusItem.put(i.getCodigo(), statusService.statusAtual(i));
            statusOs.computeIfAbsent(i.getOs().getNumero(), n -> statusService.statusAtual(i.getOs()));
        }
        Map<Integer, List<ItemRotaProcessoEntity>> rotaPorItem = itemRotaProcessoRepository.findByItemIn(itens.values())
                .stream().collect(Collectors.groupingBy(e -> e.getItem().getCodigo()));
        LocalDate hoje = LocalDate.now();
        List<EtapaFilaResponse> fila = new ArrayList<>();
        for (ItemRotaProcessoEntity e : abertas) {
            ItemEntity item = e.getItem();
            StatusItemEnum si = statusItem.get(item.getCodigo());
            StatusOsEnum so = statusOs.get(item.getOs().getNumero());
            if (si == StatusItemEnum.CANCELADA || so.isEncerrado()) {
                continue;
            }
            List<ItemRotaProcessoEntity> rota = rotaPorItem.getOrDefault(item.getCodigo(), List.of());
            boolean retrabalho = rota.stream().anyMatch(o -> o.getOrdem() < e.getOrdem()
                    && o.getRotaProcesso().getCodigo() == e.getRotaProcesso().getCodigo());
            String motivo = null;
            if (e.getDataInicio() != null) {
                motivo = "Em execução.";
            } else if (so == StatusOsEnum.PAUSADA) {
                motivo = "OS pausada.";
            } else if (si == StatusItemEnum.PAUSADA) {
                motivo = "Item pausado.";
            } else if (si == StatusItemEnum.PENDENTE) {
                motivo = "Item pendente: confira a rota, a cor pedida e a tinta.";
            } else if (rota.stream().anyMatch(o -> o.getOrdem() < e.getOrdem() && o.getDataConclusao() == null)) {
                motivo = "Aguardando a etapa anterior."; // RN-IRP-05
            } else if (RotaProcessoService.ehPintura(e.getRotaProcesso()) && item.getTinta() == null) {
                motivo = "Defina a tinta antes da pintura.";
            }
            boolean atrasado = item.getPrazoEntrega() != null && item.getPrazoEntrega().isBefore(hoje)
                    && !si.isEncerrado(); // RN-ITE-22
            FuncionarioEntity f = e.getFuncionario();
            fila.add(new EtapaFilaResponse(e.getCodigo(), item.getCodigo(), item.getDescricao(), si.getNome(),
                    item.getOs().getNumero(), item.getOs().getCliente().getPessoa().getNome(), e.getOrdem(),
                    e.getRotaProcesso().getCodigo(), e.getRotaProcesso().getNome(), e.getDataInicio(),
                    f == null ? null : f.getPessoaCodigo(), f == null ? null : f.getPessoa().getNome(),
                    item.getPrazoEntrega(), atrasado, item.getQtdTotal(), item.getUnidade(), retrabalho,
                    motivo == null, motivo));
        }
        fila.sort(Comparator.comparing(EtapaFilaResponse::prazoEntrega, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(EtapaFilaResponse::osNumero)
                .thenComparingInt(EtapaFilaResponse::itemCodigo)
                .thenComparingInt(EtapaFilaResponse::ordem));
        return fila;
    }

    // ------------------------------------------------------------------ apoio

    /** Etapas do item na ordem, com a marca de retrabalho (a mesma etapa já apareceu antes). */
    List<EtapaResponse> etapas(ItemEntity item) {
        List<ItemRotaProcessoEntity> lista = etapasDoItem(item);
        List<EtapaResponse> resposta = new ArrayList<>();
        for (ItemRotaProcessoEntity e : lista) {
            resposta.add(paraResponse(e, lista));
        }
        return resposta;
    }

    List<ItemRotaProcessoEntity> etapasDoItem(ItemEntity item) {
        return itemRotaProcessoRepository.findByItemOrderByOrdem(item);
    }

    ItemRotaProcessoEntity buscarEntidade(int codigo) {
        return itemRotaProcessoRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa do item não encontrada."));
    }

    private void novaEtapa(ItemEntity item, RotaProcessoEntity rota, int ordem) {
        ItemRotaProcessoEntity etapa = new ItemRotaProcessoEntity();
        etapa.setItem(item); // RN-IRP-12: item obrigatório; item + ordem não se repetem (o item está travado)
        etapa.setRotaProcesso(rota);
        etapa.setOrdem(ordem); // RN-IRP-13: calculada pelo Service, nunca enviada pelo front
        itemRotaProcessoRepository.save(etapa); // RN-IRP-10: código gerado pelo banco
    }

    private ItemEntity travarItem(int codigo) {
        return itemRepository.buscarParaAtualizar(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado."));
    }

    private OrdemServicoEntity travarOs(OrdemServicoEntity os) {
        return ordemServicoRepository.buscarParaAtualizar(os.getNumero())
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada."));
    }

    /** RN-OS-07: OS 'Concluída' ou 'Cancelada' não aceita alterações. */
    private void garantirOsAberta(OrdemServicoEntity os) {
        if (statusService.statusAtual(os).isEncerrado()) {
            throw new RegraNegocioException("OS encerrada: não aceita alterações.");
        }
    }

    /** RN-OS-07 e OS pausada: não se inicia nem se conclui etapa. */
    private StatusOsEnum garantirOsEmAndamento(OrdemServicoEntity os) {
        StatusOsEnum status = statusService.statusAtual(os);
        if (status.isEncerrado()) {
            throw new RegraNegocioException("OS encerrada: não aceita alterações.");
        }
        if (status == StatusOsEnum.PAUSADA) {
            throw new RegraNegocioException("OS pausada: retome a OS antes de iniciar ou concluir etapas.");
        }
        return status;
    }

    /** Item cancelado ou pausado não executa etapas; item pendente ainda não está pronto para começar. */
    private void garantirItemEmExecucao(ItemEntity item, boolean iniciando) {
        StatusItemEnum status = statusService.statusAtual(item);
        if (status == StatusItemEnum.CANCELADA) {
            throw new RegraNegocioException("Item cancelado não executa etapas.");
        }
        if (status == StatusItemEnum.PAUSADA) {
            throw new RegraNegocioException("Item pausado: retome o item antes de iniciar ou concluir etapas.");
        }
        if (iniciando && status == StatusItemEnum.PENDENTE) {
            throw new RegraNegocioException(
                    "Item ainda não está pronto para execução: confira a rota, a cor pedida e a tinta.");
        }
    }

    private boolean temEtapaRepetida(List<ItemRotaProcessoEntity> etapas) {
        Set<Integer> vistas = new HashSet<>();
        for (ItemRotaProcessoEntity e : etapas) {
            if (!vistas.add(e.getRotaProcesso().getCodigo())) {
                return true;
            }
        }
        return false;
    }

    private EtapaResponse paraResponse(ItemRotaProcessoEntity e, List<ItemRotaProcessoEntity> todas) {
        boolean retrabalho = todas.stream().anyMatch(o -> o.getOrdem() < e.getOrdem()
                && o.getRotaProcesso().getCodigo() == e.getRotaProcesso().getCodigo());
        FuncionarioEntity f = e.getFuncionario();
        return new EtapaResponse(e.getCodigo(), e.getItem().getCodigo(), e.getOrdem(), e.getRotaProcesso().getCodigo(),
                e.getRotaProcesso().getNome(), e.getDataInicio(), e.getDataConclusao(),
                f == null ? null : f.getPessoaCodigo(), f == null ? null : f.getPessoa().getNome(), retrabalho);
    }
}
