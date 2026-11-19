package br.fatec.projetoextensao.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.EventoAuditoriaResponse;
import br.fatec.projetoextensao.dtos.HistoricoStatusResponse;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.PainelOsResponse;
import br.fatec.projetoextensao.dtos.ProximaOsResponse;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.ErrosBanco;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.HistoricoStatusOsRepository;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.NumeracaoOs;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.repositories.ProcedimentosBanco;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository.CronogramaAfetado;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import jakarta.persistence.EntityManager;
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
 *
 * ALTERADO (18/11/2026) [E3-12]: integração com a Etapa 3 do banco.
 * <ul>
 *   <li>cancelar chama a procedure dbo.sp_cancelar_ordem_servico (antes: status + itens + valor, passo a passo em
 *       Java) e avisa quais cronogramas perderam etapas;</li>
 *   <li>novo painel(): lista leve das OS pela view dbo.vw_painel_ordens_servico (GET /api/ordens/painel);</li>
 *   <li>nova auditoria(numero): linha do tempo pela função dbo.fn_auditoria_timeline_os (GET /api/ordens/{n}/auditoria);</li>
 *   <li>o valor da OS vem dos triggers (abrir só relê) e o "valor incompleto" vem da view.</li>
 * </ul>
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
    // INCLUÍDO (18/11/2026) [E3-12]: procedure de cancelamento, function/view de leitura e o EntityManager para reler a
    // OS depois que a procedure mudou o banco.
    private final ProcedimentosBanco procedimentosBanco;
    private final RotinasBancoRepository rotinasBanco;
    private final EntityManager entityManager;
    // INCLUÍDO (19/11/2026): número previsto da próxima OS (tela Nova OS).
    private final NumeracaoOs numeracaoOs;

    /** INCLUÍDO (18/11/2026) [E3-12]: datas nos avisos do cancelamento (dd/MM/aaaa, como na tela). */
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
        // REMOVIDO (18/11/2026) [E3-07]: os.setValorOS(BigDecimal.ZERO.setScale(2)). A coluna valor_os deixou de ser
        // gravada pelo Java (insertable = false na entidade): a OS nasce com o DEFAULT 0 do script (RN-OS-13) e os
        // triggers somam os itens à medida que são incluídos.
        // RN-OS-17: sem prioridade informada, a OS nasce com a prioridade do cliente (calculada pelo faturamento, RN-CLI-09).
        os.setPrioridade(dto.prioridade() != null ? dto.prioridade() : prioridadeClienteService.deCliente(cliente));
        os = ordemServicoRepository.save(os);

        statusService.registrarInicial(os); // RN-OS-02: 'Criada'
        List<String> avisos = new ArrayList<>();
        for (ItemRequest item : dto.itens()) {
            itemService.criarItem(os, item, avisos);
        }
        // ALTERADO (18/11/2026) [E3-08]: era valorOsService.recalcular(os). Cada INSERT de item já disparou o trigger
        // trg_itens_sincronizar_valor_os, que gravou valor_os; sincronizar relê a OS para a resposta trazer o valor.
        valorOsService.sincronizar(os); // RN-OS-14
        return paraResponse(os, avisos);
    }

    /**
     * INCLUÍDO (19/11/2026): número previsto e data de entrada da OS que vai ser aberta, para a tela Nova OS mostrar
     * antes de salvar. Não reserva o número: quem grava continua sendo abrir().
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public ProximaOsResponse proxima() {
        return new ProximaOsResponse(numeracaoOs.proximoNumero(), LocalDateTime.now());
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
     *
     * ALTERADO (18/11/2026) [E3-12]: era, em Java, statusService.mudarStatus(os, CANCELADA) +
     * itemService.cancelarItensDaOs(os) + valorOsService.recalcular(os). Agora a procedure
     * dbo.sp_cancelar_ordem_servico faz tudo numa transação no banco: (1) cancela em lote os itens não encerrados,
     * (2) tira as etapas não concluídas desses itens dos cronogramas de hoje em diante, (3) grava 'Cancelada' no
     * histórico da OS e (4) recalcula valor_os com fn_valor_os. O passo (2) é novo: antes, a etapa de uma OS
     * cancelada continuava no cronograma.
     * Passos do Java: trava a OS; confere as regras de status (mesmas mensagens de antes) antes da procedure, porque
     * se ela recusar (THROW 50011/50012) o ROLLBACK dela desfaz a transação inteira; lê quais cronogramas vão perder
     * etapas (para avisar); chama a procedure; relê a OS (o valor mudou no banco) e devolve os avisos.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public OsResponse cancelar(int numero) {
        OrdemServicoEntity os = travar(numero);
        statusService.conferirMudanca(os, StatusOsEnum.CANCELADA); // RN-HOS-04, RN-OS-03 e RN-HOS-02
        List<CronogramaAfetado> afetados = rotinasBanco.cronogramasFuturosComEtapasDaOs(numero, LocalDate.now());
        int itensCancelados;
        try {
            itensCancelados = procedimentosBanco.cancelarOrdemServico(numero); // RN-OS-05 e RN-OS-15
        } catch (DataAccessException e) {
            throw ErrosBanco.converter(e); // THROW 50010/50011/50012 -> mensagem do script
        }
        entityManager.refresh(os); // valor_os foi regravado pela procedure
        log.info("AUDITORIA login={} acao=CANCELAR_OS os={} itensCancelados={} cronogramasAfetados={}",
                usuarioLogado.login().orElse("-"), numero, itensCancelados, afetados.size());
        return paraResponse(os, avisosDoCancelamento(itensCancelados, afetados));
    }

    /**
     * INCLUÍDO (18/11/2026) [E3-12]: o que a tela precisa saber depois do cancelamento.
     * O aviso do cronograma vazio existe porque a procedure apaga as etapas e mantém o cabeçalho do cronograma: se
     * todas as etapas do dia eram desta OS, o dia fica com um cronograma sem etapas, o que a RN-CRO-01 não permite
     * criar pela tela. O sistema não apaga o cronograma sozinho (a decisão é de quem planeja): avisa para incluir
     * outras etapas ou excluir o cronograma.
     */
    private List<String> avisosDoCancelamento(int itensCancelados, List<CronogramaAfetado> afetados) {
        List<String> avisos = new ArrayList<>();
        if (itensCancelados > 0) {
            avisos.add(itensCancelados + " item(ns) não executado(s) cancelado(s) junto com a OS (RN-OS-05).");
        }
        if (!afetados.isEmpty()) {
            int etapas = afetados.stream().mapToInt(CronogramaAfetado::etapasDaOs).sum();
            String dias = afetados.stream().map(a -> a.dataPlanejada().format(DATA_BR)).collect(Collectors.joining(", "));
            avisos.add(etapas + " etapa(s) desta OS saíram do(s) cronograma(s) de " + dias + ".");
        }
        afetados.stream().filter(CronogramaAfetado::ficaVazio).forEach(a -> avisos.add("O cronograma de "
                + a.dataPlanejada().format(DATA_BR) + " ficou sem etapas: inclua outras etapas ou exclua o cronograma (RN-CRO-01)."));
        return avisos;
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public OsResponse buscar(int numero) {
        return paraResponse(buscarEntidade(numero), List.of());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<OsResponse> listar() {
        // ALTERADO (18/11/2026) [E3-12]: o "valor incompleto" de cada OS vem da view vw_painel_ordens_servico, lida uma
        // vez só para a lista inteira (em vez de uma consulta da view por OS).
        Map<Integer, Boolean> incompletos = rotinasBanco.painelOs().stream()
                .collect(Collectors.toMap(PainelOsResponse::numero, PainelOsResponse::valorIncompleto));
        return ordemServicoRepository.findAllByOrderByNumeroDesc().stream()
                .map(os -> paraResponse(os, List.of(), incompletos.getOrDefault(os.getNumero(), false)))
                .toList();
    }

    /**
     * INCLUÍDO (18/11/2026) [E3-12]: painel das OS (GET /api/ordens/painel), direto da view dbo.vw_painel_ordens_servico.
     * Por que existe: as listas da tela (Ordens de Serviço, Clientes, Peças, relatório de OS) só precisam de cliente,
     * status, valor e contagens de itens; listar() monta cada OS com todos os itens, rotas e fotos. A view entrega
     * tudo numa consulta, com as contagens (executados, cancelados, em aberto, atrasados) já feitas no banco.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<PainelOsResponse> painel() {
        return rotinasBanco.painelOs();
    }

    /**
     * INCLUÍDO (18/11/2026) [E3-12]: linha do tempo da OS (aba Auditoria), pela função dbo.fn_auditoria_timeline_os:
     * status da OS, status de cada item e início/conclusão de cada etapa, em ordem cronológica (rastreabilidade de
     * ponta a ponta). Só leitura. OS inexistente: 404, como no histórico (RN-HOS-05).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<EventoAuditoriaResponse> auditoria(int numero) {
        buscarEntidade(numero);
        return rotinasBanco.linhaDoTempoOs(numero);
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
        return paraResponse(os, avisos, valorOsService.valorIncompleto(os));
    }

    // ALTERADO (18/11/2026) [E3-12]: o valorIncompleto passou a ser parâmetro, para listar() usar o valor já lido da
    // view para todas as OS; o paraResponse de duas entradas (acima) lê o da OS sozinha (ValorOsService -> view).
    private OsResponse paraResponse(OrdemServicoEntity os, List<String> avisos, boolean valorIncompleto) {
        List<ItemResponse> itens = itemRepository.findByOsOrderByCodigo(os).stream()
                .map(i -> itemService.paraResponse(i, List.of()))
                .toList();
        ClienteEntity c = os.getCliente();
        return new OsResponse(os.getNumero(), c.getPessoaCodigo(), c.getPessoa().getNome(),
                os.getUsuarioCadastrou().getLogin(), os.getDataEntrada(), os.getValorOS(),
                valorIncompleto, os.getPrioridade(), statusService.statusAtual(os).getNome(), itens,
                avisos);
    }
}
