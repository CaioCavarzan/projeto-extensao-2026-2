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
 * Clientes (tabelas Clientes, PessoasFisicas e PessoasJuridicas).
 *
 * Especialização (regras do grupo de 20/10):
 * <ul>
 *   <li>todo cliente é PF ou PJ, nunca os dois e nunca nenhum : o cadastro só
 *       existe como cadastrarPF ou cadastrarPJ, e ClienteEntity é abstrata;</li>
 *   <li>a pessoa pode ser cliente e funcionário, ou cliente e fornecedor: se o CPF já é de um
 *       funcionário, o cliente PF é ligado à mesma pessoa; se o CNPJ já é de um fornecedor, o cliente PJ é ligado à
 *       mesma pessoa;</li>
 *   <li>funcionário que é cliente é cliente PF, com o mesmo CPF; fornecedor que é cliente é cliente PJ,
 *       com o mesmo CNPJ.</li>
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
     * Cadastro de cliente pessoa física, com a pessoa e o cliente na mesma transação. 
     * Se o CPF já pertence a um funcionário, o cliente é ligado à mesma pessoa
     * e os dados pessoais já cadastrados são mantidos.
     */

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ClienteResponse cadastrarPF(ClientePFRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe se o cliente é pessoa física ou jurídica."); 
        }
        Validacao v = new Validacao();
        String cpf = Documentos.normalizarCpf(dto.cpf());
        if (cpf == null || cpf.isEmpty()) {
            v.erro("Informe o CPF do cliente.");
        } else if (!cpf.matches("\\d{11}")) {
            v.erro("CPF inválido ou já cadastrado."); 
        } else if (!Documentos.cpfValido(cpf)) {
            v.erro("CPF inválido."); 
        }
        String nomeSocial = v.opcional(dto.nomeSocial(), "Nome social", 200); 
        DadosCliente dc = validarDadosCliente(v, dto.observacao());
        v.verificar();

        if (pessoaFisicaRepository.existsByCpf(cpf)) {
            throw RegraNegocioException.conflito("CPF inválido ou já cadastrado."); 
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCpf(cpf);
        if (existente.isPresent()) {
            pessoa = existente.get();
            garantirSemOutroCliente(pessoa, "Este cliente já está cadastrado como pessoa jurídica."); 
            if (fornecedorRepository.existsById(pessoa.getCodigo())) {
                throw new RegraNegocioException("Fornecedor só pode ser cliente pessoa jurídica, com o mesmo CNPJ.");
            }
            avisos.add("Esta pessoa já está cadastrada como funcionário; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); 
            avisos.addAll(avisosSemelhantes(pessoa));
        }

        PessoaFisicaEntity pf = new PessoaFisicaEntity();
        pf.setPessoa(pessoa); 
        pf.setCpf(cpf);
        pf.setNomeSocial(nomeSocial);
        aplicarDadosCliente(pf, dto.fonteCodigo(), dc);
        pf = pessoaFisicaRepository.save(pf);
        return paraResponse(pf, avisos);
    }

    /**
     * Cadastro de cliente pessoa jurídica. Se o CNPJ já pertence a um fornecedor, o cliente é
     * ligado à mesma pessoa. Funcionário nunca é cliente PJ.
     */

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public ClienteResponse cadastrarPJ(ClientePJRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe se o cliente é pessoa física ou jurídica."); 
        }
        Validacao v = new Validacao();
        String cnpj = Documentos.normalizarCnpj(dto.cnpj()); 
        if (cnpj == null || cnpj.isEmpty()) {
            v.erro("Informe o CNPJ do cliente.");
        } else if (!cnpj.matches("[0-9A-Z]{12}\\d{2}")) {
            v.erro("CNPJ inválido ou já cadastrado."); 
        } else if (!Documentos.cnpjValido(cnpj)) {
            v.erro("CNPJ inválido."); 
        }
        String nomeFantasia = v.opcional(dto.nomeFantasia(), "Nome fantasia", 100); 
        String ie = validarIe(v, dto.ie()); 
        DadosCliente dc = validarDadosCliente(v, dto.observacao());
        v.verificar();

        if (pessoaJuridicaRepository.existsByCnpj(cnpj)) {
            throw RegraNegocioException.conflito("CNPJ inválido ou já cadastrado."); 
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCnpj(cnpj);
        if (existente.isPresent()) {
            pessoa = existente.get();
            garantirSemOutroCliente(pessoa, "Este cliente já está cadastrado como pessoa física."); 
            if (funcionarioRepository.existsById(pessoa.getCodigo())) {
                throw new RegraNegocioException("Funcionário só pode ser cliente pessoa física, com o mesmo CPF."); 
            }
            avisos.add("Esta pessoa já está cadastrada como fornecedor; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); 
            avisos.addAll(avisosSemelhantes(pessoa));
        }

        PessoaJuridicaEntity pj = new PessoaJuridicaEntity();
        pj.setPessoa(pessoa); 
        pj.setCnpj(cnpj);
        pj.setNomeFantasia(nomeFantasia);
        pj.setInscricaoEstadual(ie);
        aplicarDadosCliente(pj, dto.fonteCodigo(), dc);
        pj = pessoaJuridicaRepository.save(pj);
        return paraResponse(pj, avisos);
    }

    // ------------------------------------------------------------------ alteração, consulta e exclusão

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
            nomeSocial = v.opcional(dto.nomeSocial(), "Nome social", 200); 
        } else {
            nomeFantasia = v.opcional(dto.nomeFantasia(), "Nome fantasia", 100); 
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
        Map<Integer, Integer> prioridades = prioridadeClienteService.calcularTodas(); 
        return clienteRepository.findAllByOrderByPessoaNome().stream()
                .map(c -> paraResponse(c, List.of(), prioridades))
                .toList();
    }

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

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        ClienteEntity cliente = buscarEntidade(codigo);
        if (ordemServicoRepository.existsByCliente(cliente)) {
            throw RegraNegocioException.conflito("Cliente com ordens de serviço não pode ser excluído.");
        }
        PessoaEntity pessoa = cliente.getPessoa();
        clienteRepository.delete(cliente); 
        clienteRepository.flush();
        pessoaService.excluirSeSemPapel(pessoa);
    }

    ClienteEntity buscarEntidade(int codigo) {
        return clienteRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado.")); 
    }

    // ------------------------------------------------------------------ apoio

    private record DadosCliente(String observacao) {
    }

    private DadosCliente validarDadosCliente(Validacao v, String observacao) {
        return new DadosCliente(v.opcional(observacao, "Observação", 300));
    }

    private String validarIe(Validacao v, String valor) {
        String ie = Textos.maiusculas(valor);
        if (ie == null) {
            return null;
        }
        if (ie.length() > 14) {
            v.erro("Inscrição estadual: no máximo 14 caracteres."); 
        } else if (!ie.matches("\\d{1,14}|ISENTO")) {
            v.erro("IE inválida: informe só números ou ISENTO."); 
        }
        return ie;
    }

    private void aplicarDadosCliente(ClienteEntity cliente, Integer fonteCodigo, DadosCliente dc) {
        FonteAquisicaoEntity fonte = fonteCodigo == null
                ? fonteAquisicaoService.naoInformada()
                : fonteAquisicaoService.buscarEntidade(fonteCodigo);
        cliente.setFonte(fonte);
        cliente.setObservacao(dc.observacao());
    }

    private void garantirSemOutroCliente(PessoaEntity pessoa, String mensagem) {
        if (clienteRepository.existsById(pessoa.getCodigo())) {
            throw RegraNegocioException.conflito(mensagem);
        }
    }

    private List<String> avisosSemelhantes(PessoaEntity pessoa) {
        String nome = pessoa.getNome();
        boolean existeParecido = clienteRepository.findByPessoaNomeContainingIgnoreCaseOrderByPessoaNome(nome).stream()
                .anyMatch(c -> c.getPessoaCodigo() != pessoa.getCodigo());
        return existeParecido ? List.of("Já existe cliente com nome parecido. Confira se não é o mesmo cliente.") : List.of();
    }

    ClienteResponse paraResponse(ClienteEntity c, List<String> avisos) {
        return paraResponse(c, avisos, prioridadeClienteService.calcularTodas());
    }

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
