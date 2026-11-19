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
 * Ordens de serviço e o status da OS (tabelas OrdensDeServico e HistoricosStatusOs).
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
    private final ProcedimentosBanco procedimentosBanco;
    private final RotinasBancoRepository rotinasBanco;
    private final EntityManager entityManager;
    private final NumeracaoOs numeracaoOs;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse abrir(AbrirOsRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe o cliente e os itens da OS.");
            v.verificar();
        }
        v.exigir(dto.clienteCodigo() != null, "Informe o cliente."); 
        v.exigir(dto.itens() != null && !dto.itens().isEmpty(), "Inclua pelo menos um item na OS."); 
        validarPrioridade(v, dto.prioridade());
        v.verificar();

        UsuarioEntity usuario = usuarioService.usuarioLogadoEntidade(); 
        ClienteEntity cliente = clienteService.buscarEntidade(dto.clienteCodigo()); 

        OrdemServicoEntity os = new OrdemServicoEntity();
        os.setCliente(cliente);
        os.setUsuarioCadastrou(usuario);
        os.setDataEntrada(LocalDateTime.now()); 
        os.setPrioridade(dto.prioridade() != null ? dto.prioridade() : prioridadeClienteService.deCliente(cliente));
        os = ordemServicoRepository.save(os);

        statusService.registrarInicial(os); 
        List<String> avisos = new ArrayList<>();
        for (ItemRequest item : dto.itens()) {
            itemService.criarItem(os, item, avisos);
        }
        valorOsService.sincronizar(os); 
        return paraResponse(os, avisos);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public ProximaOsResponse proxima() {
        return new ProximaOsResponse(numeracaoOs.proximoNumero(), LocalDateTime.now());
    }

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
        os.setPrioridade(prioridade); 
        return paraResponse(ordemServicoRepository.save(os), List.of());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse pausar(int numero) {
        OrdemServicoEntity os = travar(numero);
        statusService.mudarStatus(os, StatusOsEnum.PAUSADA);
        return paraResponse(os, List.of());
    }

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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public OsResponse concluir(int numero) {
        OrdemServicoEntity os = travar(numero);
        if (!itemService.todosEncerrados(os)) {
            throw new RegraNegocioException("Há itens não executados nesta OS."); 
        }
        statusService.mudarStatus(os, StatusOsEnum.CONCLUIDA); 
        return paraResponse(os, List.of());
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public OsResponse cancelar(int numero) {
        OrdemServicoEntity os = travar(numero);
        statusService.conferirMudanca(os, StatusOsEnum.CANCELADA); 
        List<CronogramaAfetado> afetados = rotinasBanco.cronogramasFuturosComEtapasDaOs(numero, LocalDate.now());
        int itensCancelados;
        try {
            itensCancelados = procedimentosBanco.cancelarOrdemServico(numero); 
        } catch (DataAccessException e) {
            throw ErrosBanco.converter(e); 
        }
        entityManager.refresh(os); 
        log.info("AUDITORIA login={} acao=CANCELAR_OS os={} itensCancelados={} cronogramasAfetados={}",
                usuarioLogado.login().orElse("-"), numero, itensCancelados, afetados.size());
        return paraResponse(os, avisosDoCancelamento(itensCancelados, afetados));
    }

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
        Map<Integer, Boolean> incompletos = rotinasBanco.painelOs().stream()
                .collect(Collectors.toMap(PainelOsResponse::numero, PainelOsResponse::valorIncompleto));
        return ordemServicoRepository.findAllByOrderByNumeroDesc().stream()
                .map(os -> paraResponse(os, List.of(), incompletos.getOrDefault(os.getNumero(), false)))
                .toList();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<PainelOsResponse> painel() {
        return rotinasBanco.painelOs();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<EventoAuditoriaResponse> auditoria(int numero) {
        buscarEntidade(numero);
        return rotinasBanco.linhaDoTempoOs(numero);
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public String statusAtual(int numero) {
        return statusService.statusAtual(buscarEntidade(numero)).getNome();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<HistoricoStatusResponse> historico(int numero) {
        return historicoStatusOsRepository.findByOsOrderByDataHora(buscarEntidade(numero)).stream()
                .map(h -> new HistoricoStatusResponse(h.getStatus().getStatus(), h.getDataHora()))
                .toList();
    }

    OrdemServicoEntity buscarEntidade(int numero) {
        return ordemServicoRepository.findById(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada.")); 
    }

    private OrdemServicoEntity travar(int numero) {
        return ordemServicoRepository.buscarParaAtualizar(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada."));
    }

    private void validarPrioridade(Validacao v, Integer prioridade) {
        if (prioridade != null && (prioridade < parametros.getPrioridadeMinima() || prioridade > parametros.getPrioridadeMaxima())) {
            v.erro("Prioridade fora da escala.");
        }
    }

    private OsResponse paraResponse(OrdemServicoEntity os, List<String> avisos) {
        return paraResponse(os, avisos, valorOsService.valorIncompleto(os));
    }

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
