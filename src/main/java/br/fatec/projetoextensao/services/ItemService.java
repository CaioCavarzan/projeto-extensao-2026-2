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
import br.fatec.projetoextensao.dtos.ItemAtrasadoResponse;
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
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Imagens;
import br.fatec.projetoextensao.validacao.Textos;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * Itens da OS, status do item e fotos (tabelas Itens, HistoricosStatusItens e Fotos).
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
    private final RotinasBancoRepository rotinasBanco;

    // ------------------------------------------------------------------ inclusão

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse incluir(int osNumero, ItemRequest dto) {
        OrdemServicoEntity os = ordemServicoRepository.buscarParaAtualizar(osNumero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada.")); 
        garantirOsAberta(os);
        List<String> avisos = new ArrayList<>();
        ItemEntity item = criarItem(os, dto, avisos);
        valorOsService.sincronizar(os); 
        return paraResponse(item, avisos);
    }

    ItemEntity criarItem(OrdemServicoEntity os, ItemRequest dto, List<String> avisos) {
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do item.");
        }
        Validacao v = new Validacao();
        DadosItem d = validarDados(v, dto.descricao(), dto.qtdTotal(), dto.unidade(), dto.prazoEntrega(),
                dto.valorTotalItem(), LocalDate.now());
        if (dto.etapas() == null || dto.etapas().isEmpty()) {
            v.erro("Informe pelo menos uma etapa do processo."); 
        }
        v.verificar();

        List<RotaProcessoEntity> rotas = new ArrayList<>();
        for (Integer codigo : dto.etapas()) {
            if (codigo == null) {
                throw new RecursoNaoEncontradoException("Etapa não encontrada."); 
            }
            rotas.add(rotaProcessoService.buscarEntidade(codigo));
        }
        boolean temPintura = rotas.stream().anyMatch(RotaProcessoService::ehPintura);
        CorEntity cor = dto.corCodigo() == null ? null : corService.buscarEntidade(dto.corCodigo()); 
        TintaEntity tinta = dto.tintaCodigo() == null ? null : tintaService.buscarEntidade(dto.tintaCodigo());
        validarCorETinta(temPintura, cor, tinta, avisos);

        ItemEntity item = new ItemEntity();
        item.setOs(os); 
        item.setDescricao(d.descricao());
        item.setQtdTotal(d.qtdTotal());
        item.setUnidade(d.unidade());
        item.setCor(cor);
        item.setTinta(tinta);
        item.setPrazoEntrega(d.prazoEntrega());
        item.setValorTotalPeca(d.valor()); 
        item.setScorePrioridade(null); 
        LocalDateTime agora = LocalDateTime.now();
        item.setDataEntrada(os.getDataEntrada() != null && agora.isBefore(os.getDataEntrada()) ? os.getDataEntrada() : agora);
        item = itemRepository.save(item);

        statusService.registrarInicial(item); 
        itemRotaProcessoService.criarEtapasIniciais(item, rotas); 
        statusService.avaliarProntidao(item); 
        return item;
    }

    // ------------------------------------------------------------------ alteração

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
        valorOsService.sincronizar(item.getOs()); 
        statusService.avaliarProntidao(item);
        return paraResponse(item, avisos);
    }

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

    // ------------------------------------------------------------------ status 

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse pausar(int codigo) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        statusService.mudarStatus(item, StatusItemEnum.PAUSADA);
        return paraResponse(item, List.of());
    }

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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse cancelar(int codigo) {
        ItemEntity item = travarItem(codigo);
        garantirOsAberta(item.getOs());
        statusService.mudarStatus(item, StatusItemEnum.CANCELADA);
        valorOsService.sincronizar(item.getOs());
        return paraResponse(item, List.of());
    }

    boolean todosEncerrados(OrdemServicoEntity os) {
        return itemRepository.findByOsOrderByCodigo(os).stream()
                .allMatch(i -> statusService.statusAtual(i).isEncerrado());
    }

    // ------------------------------------------------------------------ entrega

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ItemResponse registrarEntrega(int codigo, String nrOse) {
        ItemEntity item = travarItem(codigo);
        String numero = Textos.limpar(nrOse);
        if (numero == null) {
            throw new RegraNegocioException("Informe o número da OSE assinada."); 
        }
        if (numero.length() > 50) {
            throw new RegraNegocioException("Nº da OSE: no máximo 50 caracteres."); 
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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemResponse> listarTodos() {
        return itemRepository.findAllByOrderByCodigoDesc().stream().map(i -> paraResponse(i, List.of())).toList();
    }

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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemAtrasadoResponse> listarAtrasados() {
        return rotinasBanco.itensAtrasados(LocalDate.now());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<HistoricoStatusResponse> historico(int codigo) {
        return historicoStatusItemRepository.findByItemOrderByDataHora(buscarEntidade(codigo)).stream()
                .map(h -> new HistoricoStatusResponse(h.getStatusItem().getStatus(), h.getDataHora()))
                .toList();
    }

    // ------------------------------------------------------------------ fotos

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public FotoResponse anexarFoto(int itemCodigo, byte[] conteudo) {
        ItemEntity item = travarItem(itemCodigo);
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraNegocioException("Envie a foto."); 
        }
        long limiteBytes = parametros.getTamanhoMaximoFotoMb() * 1024L * 1024L;
        String mensagemTipo = "Envie uma imagem JPG ou PNG de até " + parametros.getTamanhoMaximoFotoMb() + " MB.";
        if (conteudo.length > limiteBytes || Imagens.detectarTipo(conteudo) == null) {
            throw new RegraNegocioException(mensagemTipo); 
        }
        BufferedImage imagem = Imagens.ler(conteudo);
        if (imagem == null) {
            throw new RegraNegocioException("Arquivo inválido. Envie uma imagem JPG ou PNG."); 
        }
        if (fotoRepository.countByItem(item) >= parametros.getMaxFotosPorItem()) {
            throw new RegraNegocioException("Este item já tem " + parametros.getMaxFotosPorItem()
                    + " fotos. Exclua uma para anexar outra."); 
        }
        String caminho;
        try {
            byte[] jpg = Imagens.reduzirParaJpg(imagem, parametros.getLadoMaximoFotoPx(), parametros.getQualidadeJpg());
            caminho = armazenamentoFotos.gravar(jpg, "jpg"); 
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
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado.")); 
    }

    private ItemEntity travarItem(int codigo) {
        return itemRepository.buscarParaAtualizar(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado."));
    }

    private void garantirOsAberta(OrdemServicoEntity os) {
        if (statusService.statusAtual(os).isEncerrado()) {
            throw new RegraNegocioException("OS encerrada: não aceita alterações.");
        }
    }

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
        String descricao = v.obrigatorio(descricaoInformada, "Descrição do item", 100); 
        // RN-ITE-15: obrigatório, > 0 (CHECK ck_itens_qtd), até 2 casas (decimal(10,2)).
        BigDecimal qtdTotal = v.valor(qtd, true, BigDecimal.ZERO, false, Valores.DIGITOS_DECIMAL_10_2,
                "Quantidade deve ser maior que zero.");
        String unidade = Textos.maiusculas(unidadeInformada); 
        if (unidade == null || unidade.length() > 10) {
            v.erro("Campo obrigatório: Unidade (até 10 caracteres)."); 
        } else if (parametros.getUnidadesItem().stream().noneMatch(u -> u.trim().equalsIgnoreCase(unidade))) {
            v.erro("Unidade inválida."); 
        }
        if (prazo != null && prazo.isBefore(dataEntrada)) {
            v.erro("O prazo de entrega não pode ser anterior à entrada."); 
        }
        BigDecimal valor = v.valor(valorInformado, false, BigDecimal.ZERO, true, Valores.DIGITOS_DECIMAL_18_2,
                "Valor do item inválido.");
        return new DadosItem(descricao, qtdTotal, unidade, prazo, valor);
    }

    private void validarCorETinta(boolean temPintura, CorEntity cor, TintaEntity tinta, List<String> avisos) {
        if (temPintura && cor == null) {
            throw new RegraNegocioException("Informe a cor: o item tem pintura."); 
        }
        if (!temPintura && (cor != null || tinta != null)) {
            throw new RegraNegocioException("Item sem pintura na rota não leva cor nem tinta."); 
        }
        if (tinta == null) {
            return;
        }
        if (cor == null) {
            throw new RegraNegocioException("Para informar a tinta, informe antes a cor pedida."); 
        }
        if (tinta.getCor().getCodigo() != cor.getCodigo()) {
            throw new RegraNegocioException("A tinta escolhida é de outra cor (" + tinta.getCor().getNome()
                    + "); a cor pedida é " + cor.getNome() + "."); 
        }
        if (tinta.getEstoque() == null || tinta.getEstoque().signum() <= 0) {
            avisos.add("Atenção: tinta sem estoque."); 
        }
    }

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
