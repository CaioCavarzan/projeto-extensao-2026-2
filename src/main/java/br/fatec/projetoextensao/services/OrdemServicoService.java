package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.HistoricoStatusResponse;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.HistoricoStatusOsRepository;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): ordens de serviço e o status da OS (tabelas OrdensDeServico e HistoricosStatusOs).
 * Regras: RN-OS-01 a RN-OS-17, RN-HOS-01 a RN-HOS-07 (via StatusService), RN-SIS-04 (abertura atômica),
 * RN-SIS-08 (usuário que registrou = usuário logado) e RN-SIS-03 (cancelar OS: só ADMIN).
 *
 * Mudanças de status (aba "Fluxo de status"):
 * <ul>
 *   <li>Criada: automático, na abertura (RN-OS-02);</li>
 *   <li>Criada -> Em produção: automático, quando a 1ª etapa de algum item é iniciada (RN-OS-06,
 *       ItemRotaProcessoService.iniciarEtapa);</li>
 *   <li>Em produção ⇄ Pausada: manual (pausar / retomar);</li>
 *   <li>Em produção -> Concluída: concluir, só com todos os itens 'Executada' ou 'Cancelada' (RN-OS-04). É feito pelo
 *       usuário, e não automaticamente, para dar tempo de registrar retrabalho de itens já executados antes de
 *       encerrar a OS (OS concluída não aceita mais alterações: RN-OS-03 e RN-OS-07);</li>
 *   <li>-> Cancelada: cancelar, só ADMIN; cancela os itens não executados (RN-OS-05).</li>
 * </ul>
 *
 * RN-OS-09 (continuar a numeração que a Fênix já usa) não é do Service: nr_os é IDENTITY (RN-OS-08). Se a Fênix
 * decidir continuar a numeração do talão, o DBA ajusta o IDENTITY uma única vez antes da implantação
 * (DBCC CHECKIDENT ('OrdensDeServico', RESEED, <último número usado>)); o código Java não muda.
 */
@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private static final Logger log = LoggerFactory.getLogger(OrdemServicoService.class);

    private final OrdemServicoRepository ordemServicoRepository;
    private final HistoricoStatusOsRepository historicoStatusOsRepository;
    private final ItemRepository itemRepository;
    private final ClienteService clienteService;
    private final PrioridadeClienteService prioridadeClienteService;
    private final UsuarioService usuarioService;
    private final ItemService itemService;
    private final StatusService statusService;
    private final ValorOsService valorOsService;
    private final ParametrosSistema parametros;
    private final UsuarioLogado usuarioLogado;

    /**
     * Abre a OS com os itens, o status inicial e a rota de cada item, tudo na mesma transação (RN-OS-01, RN-OS-02,
     * RN-SIS-04): se um item for inválido, nenhuma linha nova é gravada.
     * Usuário (RN-OS-11), data de entrada (RN-OS-12) e valor (RN-OS-13/14) não vêm do front.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse abrir(AbrirOsRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe o cliente e os itens da OS.");
            v.verificar();
        }
        v.exigir(dto.clienteCodigo() != null, "Informe o cliente."); // RN-OS-10
        v.exigir(dto.itens() != null && !dto.itens().isEmpty(), "Inclua pelo menos um item na OS."); // RN-OS-01
        validarPrioridade(v, dto.prioridade());
        v.verificar();

        UsuarioEntity usuario = usuarioService.usuarioLogadoEntidade(); // RN-SIS-08 e RN-OS-11
        ClienteEntity cliente = clienteService.buscarEntidade(dto.clienteCodigo()); // RN-OS-10

        OrdemServicoEntity os = new OrdemServicoEntity();
        os.setCliente(cliente);
        os.setUsuarioCadastrou(usuario);
        os.setDataEntrada(LocalDateTime.now()); // RN-OS-12: hora do servidor
        os.setValorOS(BigDecimal.ZERO.setScale(2)); // RN-OS-13 e RN-SIS-10
        // RN-OS-17: sem prioridade informada, a OS nasce com a prioridade do cliente (calculada pelo faturamento, RN-CLI-09).
        os.setPrioridade(dto.prioridade() != null ? dto.prioridade() : prioridadeClienteService.deCliente(cliente));
        os = ordemServicoRepository.save(os);

        statusService.registrarInicial(os); // RN-OS-02: 'Criada'
        List<String> avisos = new ArrayList<>();
        for (ItemRequest item : dto.itens()) {
            itemService.criarItem(os, item, avisos);
        }
        valorOsService.recalcular(os); // RN-OS-14
        return paraResponse(os, avisos);
    }

    /** RN-OS-17: o usuário pode mudar a prioridade sugerida. OS encerrada não muda (RN-OS-07). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse alterarPrioridade(int numero, Integer prioridade) {
        OrdemServicoEntity os = travar(numero);
        if (statusService.statusAtual(os).isEncerrado()) {
            throw new RegraNegocioException("OS encerrada: não aceita alterações.");
        }
        Validacao v = new Validacao();
        validarPrioridade(v, prioridade);
        v.verificar();
        os.setPrioridade(prioridade); // RN-OS-16: não informada fica NULL
        return paraResponse(ordemServicoRepository.save(os), List.of());
    }

    /** Em produção -> Pausada (manual). RN-HOS-02 e RN-HOS-04 conferidas no StatusService. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse pausar(int numero) {
        OrdemServicoEntity os = travar(numero);
        statusService.mudarStatus(os, StatusOsEnum.PAUSADA);
        return paraResponse(os, List.of());
    }

    /** Pausada -> Em produção (manual). Criada -> Em produção é automático (RN-OS-06) e não é feito por aqui. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse retomar(int numero) {
        OrdemServicoEntity os = travar(numero);
        if (statusService.statusAtual(os) != StatusOsEnum.PAUSADA) {
            throw new RegraNegocioException("Só OS pausada pode ser retomada.");
        }
        statusService.mudarStatus(os, StatusOsEnum.EM_PRODUCAO);
        return paraResponse(os, List.of());
    }

    /** RN-OS-04: a OS passa a 'Concluída' só quando todos os itens estão 'Executada' ou 'Cancelada'. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse concluir(int numero) {
        OrdemServicoEntity os = travar(numero);
        if (!itemService.todosEncerrados(os)) {
            throw new RegraNegocioException("Há itens não executados nesta OS."); // RN-OS-04
        }
        statusService.mudarStatus(os, StatusOsEnum.CONCLUIDA); // RN-HOS-02: só a partir de 'Em produção'
        return paraResponse(os, List.of());
    }

    /**
     * Cancela a OS (RN-SIS-03: só ADMIN). RN-OS-05: os itens ainda não encerrados são cancelados e os executados
     * ficam como estão. RN-OS-03: OS concluída não é cancelada nem reaberta. RN-SIS-13: o cancelamento vai para o log.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public OsResponse cancelar(int numero) {
        OrdemServicoEntity os = travar(numero);
        statusService.mudarStatus(os, StatusOsEnum.CANCELADA);
        itemService.cancelarItensDaOs(os);
        valorOsService.recalcular(os); // RN-OS-15: item cancelado sai da soma
        log.info("AUDITORIA login={} acao=CANCELAR_OS os={}", usuarioLogado.login().orElse("-"), numero);
        return paraResponse(os, List.of());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public OsResponse buscar(int numero) {
        return paraResponse(buscarEntidade(numero), List.of());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<OsResponse> listar() {
        return ordemServicoRepository.findAllByOrderByNumeroDesc().stream()
                .map(os -> paraResponse(os, List.of()))
                .toList();
    }

    /** RN-HOS-03: status atual = registro mais recente do histórico. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public String statusAtual(int numero) {
        return statusService.statusAtual(buscarEntidade(numero)).getNome();
    }

    /** Histórico de status da OS (RN-HOS-01: só leitura; não existe método que altere o histórico). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<HistoricoStatusResponse> historico(int numero) {
        return historicoStatusOsRepository.findByOsOrderByDataHora(buscarEntidade(numero)).stream()
                .map(h -> new HistoricoStatusResponse(h.getStatus().getStatus(), h.getDataHora()))
                .toList();
    }

    OrdemServicoEntity buscarEntidade(int numero) {
        return ordemServicoRepository.findById(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada.")); // RN-HOS-05
    }

    private OrdemServicoEntity travar(int numero) {
        return ordemServicoRepository.buscarParaAtualizar(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada."));
    }

    /** RN-OS-16 e RN-OS-17: inteiro, opcional, na mesma escala da prioridade do cliente (RN-CLI-09). */
    private void validarPrioridade(Validacao v, Integer prioridade) {
        if (prioridade != null && (prioridade < parametros.getPrioridadeMinima() || prioridade > parametros.getPrioridadeMaxima())) {
            v.erro("Prioridade fora da escala.");
        }
    }

    private OsResponse paraResponse(OrdemServicoEntity os, List<String> avisos) {
        List<ItemResponse> itens = itemRepository.findByOsOrderByCodigo(os).stream()
                .map(i -> itemService.paraResponse(i, List.of()))
                .toList();
        ClienteEntity c = os.getCliente();
        return new OsResponse(os.getNumero(), c.getPessoaCodigo(), c.getPessoa().getNome(),
                os.getUsuarioCadastrou().getLogin(), os.getDataEntrada(), os.getValorOS(),
                valorOsService.valorIncompleto(os), os.getPrioridade(), statusService.statusAtual(os).getNome(), itens,
                avisos);
    }
}
