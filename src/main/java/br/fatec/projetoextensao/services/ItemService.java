package br.fatec.projetoextensao.services;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.dtos.FotoResponse;
import br.fatec.projetoextensao.dtos.HistoricoStatusResponse;
import br.fatec.projetoextensao.dtos.ItemAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.entities.CorEntity;
import br.fatec.projetoextensao.entities.FotoEntity;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.RotaProcessoEntity;
import br.fatec.projetoextensao.entities.TintaEntity;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.FotoRepository;
import br.fatec.projetoextensao.repositories.HistoricoStatusItemRepository;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Imagens;
import br.fatec.projetoextensao.validacao.Textos;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): itens da OS, status do item e fotos (tabelas Itens, HistoricosStatusItens e Fotos).
 * Regras: RN-ITE-01 a RN-ITE-29, RN-HIT-01 a RN-HIT-07 (via StatusService), RN-FOT-01 a RN-FOT-09, RN-OS-07
 * (OS encerrada não aceita alterações, exceto nr_ose e fotos) e RN-ITE-08 (alterar item recalcula o valor da OS).
 *
 * score_prioridade (RN-ITE-29) nunca vem do front e fica NULL: a regra com a fórmula do score foi retirada da
 * planilha. Quando a fórmula for definida, o cálculo entra aqui, num método chamado ao incluir e alterar o item.
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final FotoRepository fotoRepository;
    private final HistoricoStatusItemRepository historicoStatusItemRepository;
    private final StatusService statusService;
    private final ValorOsService valorOsService;
    private final ItemRotaProcessoService itemRotaProcessoService;
    private final RotaProcessoService rotaProcessoService;
    private final CorService corService;
    private final TintaService tintaService;
    private final ArmazenamentoFotos armazenamentoFotos;
    private final ParametrosSistema parametros;

    // ------------------------------------------------------------------ inclusão

    /**
     * Inclui um item numa OS já aberta (RN-OS-07: a OS não pode estar 'Concluída' nem 'Cancelada').
     * O item nasce 'Pendente' com a rota (RN-ITE-02 e RN-ITE-03) e o valor da OS é recalculado (RN-ITE-08).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse incluir(int osNumero, ItemRequest dto) {
        OrdemServicoEntity os = ordemServicoRepository.buscarParaAtualizar(osNumero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada.")); // RN-ITE-10
        garantirOsAberta(os);
        List<String> avisos = new ArrayList<>();
        ItemEntity item = criarItem(os, dto, avisos);
        valorOsService.recalcular(os); // RN-ITE-08 e RN-OS-14
        return paraResponse(item, avisos);
    }

    /**
     * Cria o item, o status inicial e a rota, na transação de quem chamou (abertura da OS ou inclusão).
     * Regras do item: RN-ITE-03 a RN-ITE-05, RN-ITE-11 a RN-ITE-20, RN-ITE-23, RN-ITE-24 e RN-ITE-29.
     */
    ItemEntity criarItem(OrdemServicoEntity os, ItemRequest dto, List<String> avisos) {
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do item.");
        }
        Validacao v = new Validacao();
        DadosItem d = validarDados(v, dto.descricao(), dto.qtdTotal(), dto.unidade(), dto.prazoEntrega(),
                dto.valorTotalItem(), LocalDate.now());
        if (dto.etapas() == null || dto.etapas().isEmpty()) {
            v.erro("Informe pelo menos uma etapa do processo."); // RN-ITE-03
        }
        v.verificar();

        List<RotaProcessoEntity> rotas = new ArrayList<>();
        for (Integer codigo : dto.etapas()) {
            if (codigo == null) {
                throw new RecursoNaoEncontradoException("Etapa não encontrada."); // RN-IRP-11
            }
            rotas.add(rotaProcessoService.buscarEntidade(codigo));
        }
        boolean temPintura = rotas.stream().anyMatch(RotaProcessoService::ehPintura);
        CorEntity cor = dto.corCodigo() == null ? null : corService.buscarEntidade(dto.corCodigo()); // RN-ITE-13
        TintaEntity tinta = dto.tintaCodigo() == null ? null : tintaService.buscarEntidade(dto.tintaCodigo());
        validarCorETinta(temPintura, cor, tinta, avisos);

        ItemEntity item = new ItemEntity();
        item.setOs(os); // RN-ITE-01 e RN-ITE-10
        item.setDescricao(d.descricao());
        item.setQtdTotal(d.qtdTotal());
        item.setUnidade(d.unidade());
        item.setCor(cor);
        item.setTinta(tinta);
        item.setPrazoEntrega(d.prazoEntrega());
        item.setValorTotalPeca(d.valor()); // RN-ITE-24: valor total do item, informado pelo usuário
        item.setScorePrioridade(null); // RN-ITE-29: calculado pelo sistema, nunca digitado
        // RN-ITE-18 e RN-ITE-19: hora do servidor, nunca antes da entrada da OS.
        LocalDateTime agora = LocalDateTime.now();
        item.setDataEntrada(os.getDataEntrada() != null && agora.isBefore(os.getDataEntrada()) ? os.getDataEntrada() : agora);
        item = itemRepository.save(item);

        statusService.registrarInicial(item); // RN-ITE-02: 'Pendente'
        itemRotaProcessoService.criarEtapasIniciais(item, rotas); // RN-ITE-03 e RN-IRP-01
        statusService.avaliarProntidao(item); // aba Fluxo de status: Pendente -> Aguardando execução
        return item;
    }

    // ------------------------------------------------------------------ alteração

    /**
     * Alteração do item. RN-ITE-01: a OS do item não muda (não há OS no DTO). RN-ITE-07: item 'Executada' ou
     * 'Cancelada' não é alterado. RN-OS-07: item de OS encerrada não é alterado. RN-ITE-08: recalcula o valor da OS.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse atualizar(int codigo, ItemAtualizacaoRequest dto) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        garantirItemAberto(item);
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do item.");
        }
        Validacao v = new Validacao();
        DadosItem d = validarDados(v, dto.descricao(), dto.qtdTotal(), dto.unidade(), dto.prazoEntrega(),
                dto.valorTotalItem(), item.getDataEntrada().toLocalDate());
        v.verificar();

        boolean temPintura = itemRotaProcessoService.etapasDoItem(item).stream()
                .map(ItemRotaProcessoEntity::getRotaProcesso).anyMatch(RotaProcessoService::ehPintura);
        CorEntity cor = dto.corCodigo() == null ? null : corService.buscarEntidade(dto.corCodigo());
        List<String> avisos = new ArrayList<>();
        validarCorETinta(temPintura, cor, item.getTinta(), avisos);

        item.setDescricao(d.descricao());
        item.setQtdTotal(d.qtdTotal());
        item.setUnidade(d.unidade());
        item.setCor(cor);
        item.setPrazoEntrega(d.prazoEntrega());
        item.setValorTotalPeca(d.valor());
        itemRepository.save(item);
        valorOsService.recalcular(item.getOs()); // RN-ITE-08
        statusService.avaliarProntidao(item);
        return paraResponse(item, avisos);
    }

    /**
     * Define (ou retira, com null) a tinta aplicada. RN-ITE-11: exige cor pedida; RN-ITE-04: a tinta tem a mesma cor
     * pedida; RN-ITE-05: só item com Pintura na rota tem tinta; RN-ITE-12: tinta sem estoque gera aviso, não erro.
     * Com a tinta definida, o item pode passar a 'Aguardando execução' (aba Fluxo de status).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse definirTinta(int codigo, Integer tintaCodigo) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        garantirItemAberto(item);
        boolean temPintura = itemRotaProcessoService.etapasDoItem(item).stream()
                .map(ItemRotaProcessoEntity::getRotaProcesso).anyMatch(RotaProcessoService::ehPintura);
        TintaEntity tinta = tintaCodigo == null ? null : tintaService.buscarEntidade(tintaCodigo);
        List<String> avisos = new ArrayList<>();
        validarCorETinta(temPintura, item.getCor(), tinta, avisos);
        item.setTinta(tinta);
        itemRepository.save(item);
        statusService.avaliarProntidao(item);
        return paraResponse(item, avisos);
    }

    // ------------------------------------------------------------------ status (RN-HIT-02: só manuais aqui)

    /** Aguardando execução -> Pausada (manual, aba Fluxo de status). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse pausar(int codigo) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        statusService.mudarStatus(item, StatusItemEnum.PAUSADA);
        return paraResponse(item, List.of());
    }

    /**
     * Pausada -> Aguardando execução (manual). Só vale para item pausado: Pendente -> Aguardando execução é
     * automático, quando o item fica pronto (aba Fluxo de status), e não pode ser forçado por aqui.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse retomar(int codigo) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        if (statusService.statusAtual(item) != StatusItemEnum.PAUSADA) {
            throw new RegraNegocioException("Só item pausado pode ser retomado.");
        }
        statusService.mudarStatus(item, StatusItemEnum.AGUARDANDO_EXECUCAO);
        return paraResponse(item, List.of());
    }

    /** -> Cancelada (manual). RN-OS-15 e RN-ITE-08: item cancelado sai da soma e o valor da OS é recalculado. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse cancelar(int codigo) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        statusService.mudarStatus(item, StatusItemEnum.CANCELADA);
        valorOsService.recalcular(item.getOs());
        return paraResponse(item, List.of());
    }

    /** RN-OS-05: ao cancelar a OS, os itens ainda não encerrados são cancelados; os executados ficam como estão. */
    void cancelarItensDaOs(OrdemServicoEntity os) {
        for (ItemEntity item : itemRepository.findByOsOrderByCodigo(os)) {
            if (!statusService.statusAtual(item).isEncerrado()) {
                statusService.mudarStatus(item, StatusItemEnum.CANCELADA);
            }
        }
    }

    /** RN-OS-04: todos os itens estão 'Executada' ou 'Cancelada'. */
    boolean todosEncerrados(OrdemServicoEntity os) {
        return itemRepository.findByOsOrderByCodigo(os).stream()
                .allMatch(i -> statusService.statusAtual(i).isEncerrado());
    }

    // ------------------------------------------------------------------ entrega

    /**
     * RN-ITE-28: nr_ose é registrado na entrega, com a OSE assinada pelo cliente; o item só é dado como entregue
     * com nr_ose. RN-ITE-27: até 50 caracteres. RN-OS-07 e RN-ITE-07: vale também com a OS ou o item encerrados.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse registrarEntrega(int codigo, String nrOse) {
        ItemEntity item = travarItem(codigo);
        String numero = Textos.limpar(nrOse);
        if (numero == null) {
            throw new RegraNegocioException("Informe o número da OSE assinada."); // RN-ITE-28
        }
        if (numero.length() > 50) {
            throw new RegraNegocioException("Nº da OSE: no máximo 50 caracteres."); // RN-ITE-27
        }
        item.setNumeroOse(numero);
        itemRepository.save(item);
        return paraResponse(item, List.of());
    }

    // ------------------------------------------------------------------ consultas

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public ItemResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo), List.of());
    }

    /** INCLUÍDO (21/10/2026): tela Peças: todos os itens, do mais recente para o mais antigo. RN-ITE-22: marca de atraso. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemResponse> listarTodos() {
        return itemRepository.findAllByOrderByCodigoDesc().stream().map(i -> paraResponse(i, List.of())).toList();
    }

    /**
     * INCLUÍDO (21/10/2026): arquivo de uma foto, para a tela mostrar a imagem. RN-FOT-01: o banco guarda só o
     * caminho relativo; o arquivo fica na pasta fenix.fotos.diretorio. Todas as fotos são gravadas em JPG (RN-FOT-09).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public Path arquivoDaFoto(int fotoCodigo) {
        FotoEntity foto = fotoRepository.findById(fotoCodigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada."));
        Path arquivo = armazenamentoFotos.resolver(foto.getFoto());
        if (!Files.isRegularFile(arquivo)) {
            throw new RecursoNaoEncontradoException("Arquivo da foto não encontrado.");
        }
        return arquivo;
    }

    /** RN-ITE-22: itens com prazo vencido (antes de hoje) e não encerrados, para aparecerem destacados. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemResponse> listarAtrasados() {
        return itemRepository.findByPrazoEntregaBeforeOrderByPrazoEntrega(LocalDate.now()).stream()
                .filter(i -> !statusService.statusAtual(i).isEncerrado())
                .map(i -> paraResponse(i, List.of()))
                .toList();
    }

    /** Histórico de status do item (RN-HIT-01: só leitura). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<HistoricoStatusResponse> historico(int codigo) {
        return historicoStatusItemRepository.findByItemOrderByDataHora(buscarEntidade(codigo)).stream()
                .map(h -> new HistoricoStatusResponse(h.getStatusItem().getStatus(), h.getDataHora()))
                .toList();
    }

    // ------------------------------------------------------------------ fotos

    /**
     * Anexa uma foto ao item. Vale também com o item ou a OS encerrados (RN-ITE-07 e RN-OS-07).
     * RN-FOT-02: no máximo 10 fotos por item (contadas com o item travado).
     * RN-FOT-06: JPG ou PNG de até 10 MB. RN-FOT-07: tipo conferido pelo conteúdo e o arquivo precisa abrir como
     * imagem. RN-FOT-09: a imagem é reduzida para no máximo 1920 px e gravada como JPG 80%.
     * RN-FOT-08: nome gerado pelo sistema. RN-FOT-01: o arquivo fica na pasta do servidor e o banco guarda o caminho.
     * RN-FOT-03: o código da foto é gerado pelo banco.
     *
     * O Controller recebe o MultipartFile e passa os bytes (multipartFile.getBytes()); o nome original do arquivo
     * não é usado.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public FotoResponse anexarFoto(int itemCodigo, byte[] conteudo) {
        ItemEntity item = travarItem(itemCodigo);
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraNegocioException("Envie a foto."); // RN-FOT-05
        }
        long limiteBytes = parametros.getTamanhoMaximoFotoMb() * 1024L * 1024L;
        String mensagemTipo = "Envie uma imagem JPG ou PNG de até " + parametros.getTamanhoMaximoFotoMb() + " MB.";
        if (conteudo.length > limiteBytes || Imagens.detectarTipo(conteudo) == null) {
            throw new RegraNegocioException(mensagemTipo); // RN-FOT-06 e RN-FOT-07
        }
        BufferedImage imagem = Imagens.ler(conteudo);
        if (imagem == null) {
            throw new RegraNegocioException("Arquivo inválido. Envie uma imagem JPG ou PNG."); // RN-FOT-07
        }
        if (fotoRepository.countByItem(item) >= parametros.getMaxFotosPorItem()) {
            throw new RegraNegocioException("Este item já tem " + parametros.getMaxFotosPorItem()
                    + " fotos. Exclua uma para anexar outra."); // RN-FOT-02
        }
        String caminho;
        try {
            byte[] jpg = Imagens.reduzirParaJpg(imagem, parametros.getLadoMaximoFotoPx(), parametros.getQualidadeJpg());
            caminho = armazenamentoFotos.gravar(jpg, "jpg"); // RN-FOT-08 e RN-FOT-09
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível salvar a foto.");
        }
        apagarArquivoSeDesfizer(caminho);
        FotoEntity foto = new FotoEntity();
        foto.setItem(item);
        foto.setFoto(caminho);
        foto = fotoRepository.save(foto);
        return new FotoResponse(foto.getCodigo(), item.getCodigo(), foto.getFoto());
    }

    /** RN-FOT-01: excluir a foto apaga também o arquivo (depois que a exclusão no banco é confirmada). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public void removerFoto(int fotoCodigo) {
        FotoEntity foto = fotoRepository.findById(fotoCodigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada."));
        String caminho = foto.getFoto();
        fotoRepository.delete(foto);
        apagarArquivoDepoisDeConfirmar(caminho);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<FotoResponse> listarFotos(int itemCodigo) {
        ItemEntity item = buscarEntidade(itemCodigo);
        return fotoRepository.findByItemOrderByCodigo(item).stream()
                .map(f -> new FotoResponse(f.getCodigo(), item.getCodigo(), f.getFoto()))
                .toList();
    }

    // ------------------------------------------------------------------ apoio

    ItemEntity buscarEntidade(int codigo) {
        return itemRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado.")); // RN-HIT-05, RN-FOT-04
    }

    private ItemEntity travarItem(int codigo) {
        return itemRepository.buscarParaAtualizar(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado."));
    }

    /** RN-OS-07: OS 'Concluída' ou 'Cancelada' não aceita alterações. */
    private void garantirOsAberta(OrdemServicoEntity os) {
        if (statusService.statusAtual(os).isEncerrado()) {
            throw new RegraNegocioException("OS encerrada: não aceita alterações.");
        }
    }

    /** RN-ITE-07: item 'Executada' ou 'Cancelada' não tem os dados alterados. */
    private void garantirItemAberto(ItemEntity item) {
        if (statusService.statusAtual(item).isEncerrado()) {
            throw new RegraNegocioException("Item encerrado não pode ser alterado.");
        }
    }

    private record DadosItem(String descricao, BigDecimal qtdTotal, String unidade, LocalDate prazoEntrega,
                             BigDecimal valor) {
    }

    private DadosItem validarDados(Validacao v, String descricaoInformada, BigDecimal qtd, String unidadeInformada,
                                   LocalDate prazo, BigDecimal valorInformado, LocalDate dataEntrada) {
        String descricao = v.obrigatorio(descricaoInformada, "Descrição do item", 100); // RN-ITE-14
        // RN-ITE-15: obrigatório, > 0 (CHECK ck_itens_qtd), até 2 casas (decimal(10,2)).
        BigDecimal qtdTotal = v.valor(qtd, true, BigDecimal.ZERO, false, Valores.DIGITOS_DECIMAL_10_2,
                "Quantidade deve ser maior que zero.");
        String unidade = Textos.maiusculas(unidadeInformada); // RN-ITE-17: gravada em maiúsculas
        if (unidade == null || unidade.length() > 10) {
            v.erro("Campo obrigatório: Unidade (até 10 caracteres)."); // RN-ITE-16
        } else if (parametros.getUnidadesItem().stream().noneMatch(u -> u.trim().equalsIgnoreCase(unidade))) {
            v.erro("Unidade inválida."); // RN-ITE-17
        }
        if (prazo != null && prazo.isBefore(dataEntrada)) {
            v.erro("O prazo de entrega não pode ser anterior à entrada."); // RN-ITE-20 (CHECK ck_itens_prazo)
        }
        // RN-ITE-23 e RN-ITE-24: opcional, >= 0 (CHECK ck_itens_valor), 2 casas; é o valor total do item.
        BigDecimal valor = v.valor(valorInformado, false, BigDecimal.ZERO, true, Valores.DIGITOS_DECIMAL_18_2,
                "Valor do item inválido.");
        return new DadosItem(descricao, qtdTotal, unidade, prazo, valor);
    }

    /** RN-ITE-04, RN-ITE-05, RN-ITE-11 e RN-ITE-12. */
    private void validarCorETinta(boolean temPintura, CorEntity cor, TintaEntity tinta, List<String> avisos) {
        if (temPintura && cor == null) {
            throw new RegraNegocioException("Informe a cor: o item tem pintura."); // RN-ITE-05
        }
        if (!temPintura && (cor != null || tinta != null)) {
            throw new RegraNegocioException("Item sem pintura na rota não leva cor nem tinta."); // RN-ITE-05
        }
        if (tinta == null) {
            return;
        }
        if (cor == null) {
            throw new RegraNegocioException("Para informar a tinta, informe antes a cor pedida."); // RN-ITE-11
        }
        if (tinta.getCor().getCodigo() != cor.getCodigo()) {
            throw new RegraNegocioException("A tinta escolhida é de outra cor (" + tinta.getCor().getNome()
                    + "); a cor pedida é " + cor.getNome() + "."); // RN-ITE-04
        }
        if (tinta.getEstoque() == null || tinta.getEstoque().signum() <= 0) {
            avisos.add("Atenção: tinta sem estoque."); // RN-ITE-12
        }
    }

    /** Se a transação for desfeita, o arquivo gravado não fica solto na pasta (RN-FOT-01). */
    private void apagarArquivoSeDesfizer(String caminho) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        armazenamentoFotos.apagar(caminho);
                    }
                }
            });
        }
    }

    private void apagarArquivoDepoisDeConfirmar(String caminho) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    armazenamentoFotos.apagar(caminho);
                }
            });
        } else {
            armazenamentoFotos.apagar(caminho);
        }
    }

    ItemResponse paraResponse(ItemEntity i, List<String> avisos) {
        StatusItemEnum status = statusService.statusAtual(i);
        boolean atrasado = i.getPrazoEntrega() != null && i.getPrazoEntrega().isBefore(LocalDate.now())
                && !status.isEncerrado(); // RN-ITE-22
        return new ItemResponse(i.getCodigo(), i.getOs().getNumero(), i.getDescricao(), i.getQtdTotal(), i.getUnidade(),
                i.getCor() == null ? null : i.getCor().getCodigo(), i.getCor() == null ? null : i.getCor().getNome(),
                i.getTinta() == null ? null : i.getTinta().getCodigo(),
                i.getTinta() == null ? null : i.getTinta().getDescricao(), i.getDataEntrada(), i.getPrazoEntrega(),
                atrasado, i.getValorTotalPeca(), i.getDataFinalizacao(), i.getNumeroOse(), i.getNumeroOse() != null,
                i.getScorePrioridade(), status.getNome(), itemRotaProcessoService.etapas(i), fotoRepository.countByItem(i),
                avisos);
    }
}
