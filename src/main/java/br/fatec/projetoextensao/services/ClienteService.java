package br.fatec.projetoextensao.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ClienteAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.ClientePJRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.PessoaFisicaEntity;
import br.fatec.projetoextensao.entities.PessoaJuridicaEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ClienteRepository;
import br.fatec.projetoextensao.repositories.FornecedorRepository;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.repositories.PessoaFisicaRepository;
import br.fatec.projetoextensao.repositories.PessoaJuridicaRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Documentos;
import br.fatec.projetoextensao.validacao.Textos;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): clientes (tabelas Clientes, PessoasFisicas e PessoasJuridicas).
 *
 * Especialização (regras do grupo de 20/10):
 * <ul>
 *   <li>todo cliente é PF ou PJ, nunca os dois e nunca nenhum (RN-CLI-01, RN-PF-01, RN-PJ-01): o cadastro só
 *       existe como cadastrarPF ou cadastrarPJ, e ClienteEntity é abstrata;</li>
 *   <li>a pessoa pode ser cliente e funcionário, ou cliente e fornecedor (RN-PES-02): se o CPF já é de um
 *       funcionário, o cliente PF é ligado à mesma pessoa; se o CNPJ já é de um fornecedor, o cliente PJ é ligado à
 *       mesma pessoa;</li>
 *   <li>funcionário que é cliente é cliente PF, com o mesmo CPF (RN-PES-03); fornecedor que é cliente é cliente PJ,
 *       com o mesmo CNPJ (RN-PES-04).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PessoaFisicaRepository pessoaFisicaRepository;
    private final PessoaJuridicaRepository pessoaJuridicaRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final FornecedorRepository fornecedorRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final PessoaService pessoaService;
    private final FonteAquisicaoService fonteAquisicaoService;
    private final PrioridadeClienteService prioridadeClienteService;

    // ------------------------------------------------------------------ cadastro

    /**
     * Cadastro de cliente pessoa física (RN-PF-01 a RN-PF-05), com a pessoa e o cliente na mesma transação
     * (RN-SIS-04). Se o CPF já pertence a um funcionário, o cliente é ligado à mesma pessoa (RN-PES-02 e RN-PES-03)
     * e os dados pessoais já cadastrados são mantidos.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ClienteResponse cadastrarPF(ClientePFRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe se o cliente é pessoa física ou jurídica."); // RN-CLI-01
        }
        Validacao v = new Validacao();
        String cpf = Documentos.normalizarCpf(dto.cpf());
        if (cpf == null || cpf.isEmpty()) {
            v.erro("Informe o CPF do cliente.");
        } else if (!cpf.matches("\\d{11}")) {
            v.erro("CPF inválido ou já cadastrado."); // RN-PF-03 (formato do CHECK)
        } else if (!Documentos.cpfValido(cpf)) {
            v.erro("CPF inválido."); // RN-PF-04
        }
        String nomeSocial = v.opcional(dto.nomeSocial(), "Nome social", 200); // RN-PF-05
        DadosCliente dc = validarDadosCliente(v, dto.observacao());
        v.verificar();

        if (pessoaFisicaRepository.existsByCpf(cpf)) {
            throw RegraNegocioException.conflito("CPF inválido ou já cadastrado."); // RN-PF-03
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCpf(cpf);
        if (existente.isPresent()) {
            // RN-PES-02: o CPF é de um funcionário -> o papel de cliente reaproveita a mesma pessoa.
            pessoa = existente.get();
            garantirSemOutroCliente(pessoa, "Este cliente já está cadastrado como pessoa jurídica."); // RN-PF-01
            if (fornecedorRepository.existsById(pessoa.getCodigo())) {
                // RN-PES-04 (não acontece se as regras foram seguidas: fornecedor não tem CPF)
                throw new RegraNegocioException("Fornecedor só pode ser cliente pessoa jurídica, com o mesmo CNPJ.");
            }
            avisos.add("Esta pessoa já está cadastrada como funcionário; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); // RN-PES-01: a pessoa nasce junto com o papel
            avisos.addAll(avisosSemelhantes(pessoa));
        }

        PessoaFisicaEntity pf = new PessoaFisicaEntity();
        pf.setPessoa(pessoa); // RN-CLI-04 e RN-PF-02: a chave é o código da pessoa (@MapsId)
        pf.setCpf(cpf);
        pf.setNomeSocial(nomeSocial);
        aplicarDadosCliente(pf, dto.fonteCodigo(), dc);
        pf = pessoaFisicaRepository.save(pf);
        return paraResponse(pf, avisos);
    }

    /**
     * Cadastro de cliente pessoa jurídica (RN-PJ-01 a RN-PJ-07). Se o CNPJ já pertence a um fornecedor, o cliente é
     * ligado à mesma pessoa (RN-PES-02 e RN-PES-04). Funcionário nunca é cliente PJ (RN-PES-03).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ClienteResponse cadastrarPJ(ClientePJRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe se o cliente é pessoa física ou jurídica."); // RN-CLI-01
        }
        Validacao v = new Validacao();
        String cnpj = Documentos.normalizarCnpj(dto.cnpj()); // RN-PJ-03: sem máscara e em maiúsculas
        if (cnpj == null || cnpj.isEmpty()) {
            v.erro("Informe o CNPJ do cliente.");
        } else if (!cnpj.matches("[0-9A-Z]{12}\\d{2}")) {
            v.erro("CNPJ inválido ou já cadastrado."); // RN-PJ-03 (formato do CHECK)
        } else if (!Documentos.cnpjValido(cnpj)) {
            v.erro("CNPJ inválido."); // RN-PJ-04
        }
        String nomeFantasia = v.opcional(dto.nomeFantasia(), "Nome fantasia", 100); // RN-PJ-05
        String ie = validarIe(v, dto.ie()); // RN-PJ-06 e RN-PJ-07
        DadosCliente dc = validarDadosCliente(v, dto.observacao());
        v.verificar();

        if (pessoaJuridicaRepository.existsByCnpj(cnpj)) {
            throw RegraNegocioException.conflito("CNPJ inválido ou já cadastrado."); // RN-PJ-03
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCnpj(cnpj);
        if (existente.isPresent()) {
            // RN-PES-02: o CNPJ é de um fornecedor -> o papel de cliente reaproveita a mesma pessoa.
            pessoa = existente.get();
            garantirSemOutroCliente(pessoa, "Este cliente já está cadastrado como pessoa física."); // RN-PJ-01
            if (funcionarioRepository.existsById(pessoa.getCodigo())) {
                throw new RegraNegocioException("Funcionário só pode ser cliente pessoa física, com o mesmo CPF."); // RN-PES-03
            }
            avisos.add("Esta pessoa já está cadastrada como fornecedor; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); // RN-PES-01
            avisos.addAll(avisosSemelhantes(pessoa));
        }

        PessoaJuridicaEntity pj = new PessoaJuridicaEntity();
        pj.setPessoa(pessoa); // RN-CLI-04 e RN-PJ-02
        pj.setCnpj(cnpj);
        pj.setNomeFantasia(nomeFantasia);
        pj.setInscricaoEstadual(ie);
        aplicarDadosCliente(pj, dto.fonteCodigo(), dc);
        pj = pessoaJuridicaRepository.save(pj);
        return paraResponse(pj, avisos);
    }

    // ------------------------------------------------------------------ alteração, consulta e exclusão

    /**
     * Alteração do cliente e dos dados da pessoa. O CPF/CNPJ é corrigido por PessoaService.corrigirCpf/corrigirCnpj
     * (só ADMIN), que mantém iguais os documentos dos outros papéis da mesma pessoa (RN-PES-03, RN-PES-04).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ClienteResponse atualizar(int codigo, ClienteAtualizacaoRequest dto) {
        ClienteEntity cliente = buscarEntidade(codigo);
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do cliente.");
        }
        Validacao v = new Validacao();
        String nomeSocial = null;
        String nomeFantasia = null;
        String ie = null;
        if (cliente instanceof PessoaFisicaEntity) {
            nomeSocial = v.opcional(dto.nomeSocial(), "Nome social", 200); // RN-PF-05
        } else {
            nomeFantasia = v.opcional(dto.nomeFantasia(), "Nome fantasia", 100); // RN-PJ-05
            ie = validarIe(v, dto.ie()); // RN-PJ-06 e RN-PJ-07
        }
        DadosCliente dc = validarDadosCliente(v, dto.observacao());
        v.verificar();

        pessoaService.atualizarPessoa(cliente.getPessoa(), dto.pessoa());
        if (cliente instanceof PessoaFisicaEntity pf) {
            pf.setNomeSocial(nomeSocial);
        } else if (cliente instanceof PessoaJuridicaEntity pj) {
            pj.setNomeFantasia(nomeFantasia);
            pj.setInscricaoEstadual(ie);
        }
        aplicarDadosCliente(cliente, dto.fonteCodigo(), dc);
        return paraResponse(clienteRepository.save(cliente), List.of());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public ClienteResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo), List.of());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        Map<Integer, Integer> prioridades = prioridadeClienteService.calcularTodas(); // uma vez só para a lista toda
        return clienteRepository.findAllByOrderByPessoaNome().stream()
                .map(c -> paraResponse(c, List.of(), prioridades))
                .toList();
    }

    /**
     * RN-CLI-03: clientes com nome parecido, para o front avisar de possível duplicidade antes de cadastrar.
     * Não bloqueia: o usuário decide se continua.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ClienteResponse> buscarSemelhantes(String nome) {
        String trecho = Textos.limpar(nome);
        if (trecho == null || trecho.length() < 3) {
            return List.of();
        }
        Map<Integer, Integer> prioridades = prioridadeClienteService.calcularTodas();
        return clienteRepository.findByPessoaNomeContainingIgnoreCaseOrderByPessoaNome(trecho).stream()
                .map(c -> paraResponse(c, List.of(), prioridades))
                .toList();
    }

    /**
     * RN-CLI-02: cliente com OS não é excluído. RN-SIS-03: excluir cadastro é só do ADMIN.
     * RN-PES-07: se a pessoa não tiver outro papel, ela e o endereço sem dono também são excluídos.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        ClienteEntity cliente = buscarEntidade(codigo);
        if (ordemServicoRepository.existsByCliente(cliente)) {
            throw RegraNegocioException.conflito("Cliente com ordens de serviço não pode ser excluído.");
        }
        PessoaEntity pessoa = cliente.getPessoa();
        clienteRepository.delete(cliente); // apaga a linha de PessoasFisicas/PessoasJuridicas e a de Clientes
        clienteRepository.flush();
        pessoaService.excluirSeSemPapel(pessoa);
    }

    ClienteEntity buscarEntidade(int codigo) {
        return clienteRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado.")); // RN-OS-10
    }

    // ------------------------------------------------------------------ apoio

    private record DadosCliente(String observacao) {
    }

    /**
     * RN-CLI-07 (observação até 300). ALTERADO (22/10/2026): a prioridade (RN-CLI-08 e RN-CLI-09) deixou de ser
     * digitada: é calculada pelo sistema a partir do faturamento (PrioridadeClienteService).
     */
    private DadosCliente validarDadosCliente(Validacao v, String observacao) {
        return new DadosCliente(v.opcional(observacao, "Observação", 300));
    }

    /** RN-PJ-06 (até 14 caracteres) e RN-PJ-07 (só números ou ISENTO, em maiúsculas). */
    private String validarIe(Validacao v, String valor) {
        String ie = Textos.maiusculas(valor);
        if (ie == null) {
            return null;
        }
        if (ie.length() > 14) {
            v.erro("Inscrição estadual: no máximo 14 caracteres."); // RN-PJ-06
        } else if (!ie.matches("\\d{1,14}|ISENTO")) {
            v.erro("IE inválida: informe só números ou ISENTO."); // RN-PJ-07
        }
        return ie;
    }

    /** RN-CLI-05 (fonte existe) e RN-CLI-06 (sem fonte informada = 'Não informada'). */
    private void aplicarDadosCliente(ClienteEntity cliente, Integer fonteCodigo, DadosCliente dc) {
        FonteAquisicaoEntity fonte = fonteCodigo == null
                ? fonteAquisicaoService.naoInformada()
                : fonteAquisicaoService.buscarEntidade(fonteCodigo);
        cliente.setFonte(fonte);
        cliente.setObservacao(dc.observacao());
    }

    /** RN-PF-01 e RN-PJ-01: a mesma pessoa não pode ter registro de cliente PF e PJ. */
    private void garantirSemOutroCliente(PessoaEntity pessoa, String mensagem) {
        if (clienteRepository.existsById(pessoa.getCodigo())) {
            throw RegraNegocioException.conflito(mensagem);
        }
    }

    /** RN-CLI-03: aviso (não erro) quando já existe outro cliente com nome parecido. */
    private List<String> avisosSemelhantes(PessoaEntity pessoa) {
        String nome = pessoa.getNome();
        boolean existeParecido = clienteRepository.findByPessoaNomeContainingIgnoreCaseOrderByPessoaNome(nome).stream()
                .anyMatch(c -> c.getPessoaCodigo() != pessoa.getCodigo());
        return existeParecido ? List.of("Já existe cliente com nome parecido. Confira se não é o mesmo cliente.") : List.of();
    }

    ClienteResponse paraResponse(ClienteEntity c, List<String> avisos) {
        return paraResponse(c, avisos, prioridadeClienteService.calcularTodas());
    }

    /** prioridade: calculada (RN-CLI-09), com os valores de todos os clientes já prontos. */
    ClienteResponse paraResponse(ClienteEntity c, List<String> avisos, Map<Integer, Integer> prioridades) {
        PessoaEntity p = c.getPessoa();
        String tipo = c instanceof PessoaFisicaEntity ? "PF" : "PJ";
        String cpf = null;
        String cnpj = null;
        String nomeSocial = null;
        String nomeFantasia = null;
        String ie = null;
        if (c instanceof PessoaFisicaEntity pf) {
            cpf = pf.getCpf();
            nomeSocial = pf.getNomeSocial();
        } else if (c instanceof PessoaJuridicaEntity pj) {
            cnpj = pj.getCnpj();
            nomeFantasia = pj.getNomeFantasia();
            ie = pj.getInscricaoEstadual();
        }
        String nomeExibicao = nomeSocial != null ? nomeSocial : p.getNome(); // RN-PF-06
        return new ClienteResponse(c.getPessoaCodigo(), tipo, p.getNome(), nomeExibicao, p.getEmail(), p.getTelefone(),
                PessoaService.paraResponse(p.getEndereco()), cpf, cnpj, nomeSocial, nomeFantasia, ie,
                c.getFonte().getCodigo(), c.getFonte().getDescricao(), c.getObservacao(), prioridades.get(c.getPessoaCodigo()),
                pessoaService.papeis(c.getPessoaCodigo()), avisos);
    }
}
