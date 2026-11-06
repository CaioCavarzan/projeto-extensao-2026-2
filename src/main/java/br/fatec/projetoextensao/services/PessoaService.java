package br.fatec.projetoextensao.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.EnderecoResponse;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.EnderecoEntity;
import br.fatec.projetoextensao.entities.FornecedorEntity;
import br.fatec.projetoextensao.entities.FuncionarioEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.PessoaFisicaEntity;
import br.fatec.projetoextensao.entities.PessoaJuridicaEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ClienteRepository;
import br.fatec.projetoextensao.repositories.EnderecoRepository;
import br.fatec.projetoextensao.repositories.FornecedorRepository;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.PessoaFisicaRepository;
import br.fatec.projetoextensao.repositories.PessoaJuridicaRepository;
import br.fatec.projetoextensao.repositories.PessoaRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Documentos;
import br.fatec.projetoextensao.validacao.Textos;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * ALTERADO (20/10/2026): a classe existia vazia (só com o PessoaRepository injetado por @Autowired no campo).
 * Agora concentra as regras de Pessoa e Endereço e a regra de especialização do grupo (20/10):
 * <ul>
 *   <li>toda pessoa é obrigatoriamente cliente, funcionário ou fornecedor: não existe cadastro de pessoa avulsa
 *       (RN-PES-01). A pessoa nasce junto com o papel, na mesma transação, e é excluída quando perde o último papel
 *       (RN-PES-07);</li>
 *   <li>a pessoa pode ser cliente e funcionário, ou cliente e fornecedor, ao mesmo tempo: o papel novo reaproveita
 *       a pessoa encontrada pelo CPF ou CNPJ (RN-PES-02, métodos buscarPorCpf e buscarPorCnpj);</li>
 *   <li>funcionário nunca é fornecedor, e vice-versa (RN-PES-05): conferido no FuncionarioService e no
 *       FornecedorService;</li>
 *   <li>o mesmo CPF (ou CNPJ) não pertence a duas pessoas diferentes (RN-PES-06).</li>
 * </ul>
 * Também valida os dados da pessoa (RN-PES-09 a RN-PES-14) e do endereço (RN-END-01 a RN-END-10).
 *
 * ALTERADO: a injeção passou de @Autowired no campo para construtor (@RequiredArgsConstructor do Lombok), o
 * padrão usado em todos os Services novos: as dependências ficam final e a classe pode ser testada sem o Spring.
 */
@Service
@RequiredArgsConstructor
public class PessoaService {

    private static final Logger log = LoggerFactory.getLogger(PessoaService.class);

    public static final String PAPEL_CLIENTE_PF = "Cliente PF";
    public static final String PAPEL_CLIENTE_PJ = "Cliente PJ";
    public static final String PAPEL_FUNCIONARIO = "Funcionário";
    public static final String PAPEL_FORNECEDOR = "Fornecedor";

    private final PessoaRepository pessoaRepository;
    private final EnderecoRepository enderecoRepository;
    private final ClienteRepository clienteRepository;
    private final PessoaFisicaRepository pessoaFisicaRepository;
    private final PessoaJuridicaRepository pessoaJuridicaRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final FornecedorRepository fornecedorRepository;
    private final UsuarioLogado usuarioLogado;

    // ------------------------------------------------------------------ criação e alteração (uso interno)

    /**
     * RN-PES-01: cria a pessoa e o endereço. Só é chamado pelos Services de cliente, funcionário e fornecedor,
     * dentro da transação que grava o papel: se o papel falhar, a pessoa também não fica gravada (RN-SIS-04).
     * RN-END-01: o endereço é criado junto com a pessoa, então nunca fica endereço solto.
     */
    PessoaEntity criarPessoa(PessoaRequest dto) {
        DadosPessoa d = validar(dto);
        if (d.email() != null && pessoaRepository.existsByEmailIgnoreCase(d.email())) {
            throw RegraNegocioException.conflito("E-mail inválido ou já cadastrado."); // RN-PES-10
        }
        EnderecoEntity endereco = new EnderecoEntity();
        preencher(endereco, d.endereco());
        endereco = enderecoRepository.save(endereco);

        PessoaEntity pessoa = new PessoaEntity();
        pessoa.setNome(d.nome());
        pessoa.setEmail(d.email());
        pessoa.setTelefone(d.telefone());
        pessoa.setEndereco(endereco); // RN-PES-14
        return pessoaRepository.save(pessoa);
    }

    /** Alteração dos dados comuns da pessoa, feita a partir de qualquer um dos papéis. */
    void atualizarPessoa(PessoaEntity pessoa, PessoaRequest dto) {
        DadosPessoa d = validar(dto);
        if (d.email() != null && pessoaRepository.existsByEmailIgnoreCaseAndCodigoNot(d.email(), pessoa.getCodigo())) {
            throw RegraNegocioException.conflito("E-mail inválido ou já cadastrado."); // RN-PES-10
        }
        pessoa.setNome(d.nome());
        pessoa.setEmail(d.email());
        pessoa.setTelefone(d.telefone());
        atualizarEndereco(pessoa, d.endereco());
        pessoaRepository.save(pessoa);
    }

    /**
     * RN-PES-07 e RN-END-01: depois de excluir um papel, a pessoa que não tem mais nenhum papel é excluída, e o
     * endereço que ficar sem dono também.
     */
    void excluirSeSemPapel(PessoaEntity pessoa) {
        int codigo = pessoa.getCodigo();
        if (clienteRepository.existsById(codigo) || funcionarioRepository.existsById(codigo)
                || fornecedorRepository.existsById(codigo)) {
            return;
        }
        EnderecoEntity endereco = pessoa.getEndereco();
        pessoaRepository.delete(pessoa);
        pessoaRepository.flush();
        if (pessoaRepository.countByEndereco(endereco) == 0) {
            enderecoRepository.delete(endereco);
        }
    }

    // ------------------------------------------------------------------ especialização (uso interno)

    /**
     * RN-PES-02: localiza a pessoa pelo CPF, em Funcionarios ou em PessoasFisicas, para o papel novo reaproveitar
     * a mesma pessoa (mesmo código) em vez de criar outra.
     */
    Optional<PessoaEntity> buscarPorCpf(String cpf) {
        Optional<FuncionarioEntity> funcionario = funcionarioRepository.findByCpf(cpf);
        if (funcionario.isPresent()) {
            return Optional.of(funcionario.get().getPessoa());
        }
        return pessoaFisicaRepository.findByCpf(cpf).map(ClienteEntity::getPessoa);
    }

    /** RN-PES-02: localiza a pessoa pelo CNPJ, em Fornecedores ou em PessoasJuridicas. */
    Optional<PessoaEntity> buscarPorCnpj(String cnpj) {
        Optional<FornecedorEntity> fornecedor = fornecedorRepository.findByCnpj(cnpj);
        if (fornecedor.isPresent()) {
            return Optional.of(fornecedor.get().getPessoa());
        }
        return pessoaJuridicaRepository.findByCnpj(cnpj).map(ClienteEntity::getPessoa);
    }

    /** Papéis que a pessoa tem hoje (RN-PES-02: pode ter mais de um). */
    List<String> papeis(int pessoaCodigo) {
        List<String> papeis = new ArrayList<>();
        clienteRepository.findById(pessoaCodigo).ifPresent(c ->
                papeis.add(c instanceof PessoaFisicaEntity ? PAPEL_CLIENTE_PF : PAPEL_CLIENTE_PJ));
        if (funcionarioRepository.existsById(pessoaCodigo)) {
            papeis.add(PAPEL_FUNCIONARIO);
        }
        if (fornecedorRepository.existsById(pessoaCodigo)) {
            papeis.add(PAPEL_FORNECEDOR);
        }
        return papeis;
    }

    // ------------------------------------------------------------------ correção de documento (ADMIN)

    /**
     * Correção de CPF digitado errado. Só ADMIN (RN-SIS-03: alteração sensível de cadastro).
     * Mantém as regras de especialização: o CPF do funcionário e o do cliente PF da mesma pessoa são o mesmo
     * (RN-PES-03), então os dois são corrigidos juntos; e o CPF não pode pertencer a outra pessoa (RN-PES-06).
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public List<String> corrigirCpf(int pessoaCodigo, String novoCpf) {
        buscarEntidade(pessoaCodigo);
        String cpf = Documentos.normalizarCpf(novoCpf);
        if (!Documentos.cpfValido(cpf)) {
            throw new RegraNegocioException("CPF inválido."); // RN-PF-04, RN-FUN-05
        }
        Optional<FuncionarioEntity> funcionario = funcionarioRepository.findById(pessoaCodigo);
        Optional<PessoaFisicaEntity> pf = clienteRepository.findById(pessoaCodigo)
                .filter(PessoaFisicaEntity.class::isInstance).map(PessoaFisicaEntity.class::cast);
        if (funcionario.isEmpty() && pf.isEmpty()) {
            throw new RegraNegocioException("Esta pessoa não tem CPF: não é funcionário nem cliente pessoa física.");
        }
        boolean deOutraPessoa = funcionarioRepository.findByCpf(cpf).filter(f -> f.getPessoaCodigo() != pessoaCodigo).isPresent()
                || pessoaFisicaRepository.findByCpf(cpf).filter(c -> c.getPessoaCodigo() != pessoaCodigo).isPresent();
        if (deOutraPessoa) {
            throw RegraNegocioException.conflito("CPF já cadastrado para outra pessoa."); // RN-PES-06
        }
        funcionario.ifPresent(f -> {
            f.setCpf(cpf);
            funcionarioRepository.save(f);
        });
        pf.ifPresent(c -> {
            c.setCpf(cpf);
            pessoaFisicaRepository.save(c);
        });
        auditar("CORRIGIR_CPF", pessoaCodigo);
        return papeis(pessoaCodigo);
    }

    /**
     * Correção de CNPJ digitado errado. Só ADMIN. O CNPJ do fornecedor e o do cliente PJ da mesma pessoa são o
     * mesmo (RN-PES-04), e o CNPJ não pode pertencer a outra pessoa (RN-PES-06).
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public List<String> corrigirCnpj(int pessoaCodigo, String novoCnpj) {
        buscarEntidade(pessoaCodigo);
        String cnpj = Documentos.normalizarCnpj(novoCnpj);
        if (!Documentos.cnpjValido(cnpj)) {
            throw new RegraNegocioException("CNPJ inválido."); // RN-PJ-04, RN-FOR-06
        }
        Optional<FornecedorEntity> fornecedor = fornecedorRepository.findById(pessoaCodigo);
        Optional<PessoaJuridicaEntity> pj = clienteRepository.findById(pessoaCodigo)
                .filter(PessoaJuridicaEntity.class::isInstance).map(PessoaJuridicaEntity.class::cast);
        if (fornecedor.isEmpty() && pj.isEmpty()) {
            throw new RegraNegocioException("Esta pessoa não tem CNPJ: não é fornecedor nem cliente pessoa jurídica.");
        }
        boolean deOutraPessoa = fornecedorRepository.findByCnpj(cnpj).filter(f -> f.getPessoaCodigo() != pessoaCodigo).isPresent()
                || pessoaJuridicaRepository.findByCnpj(cnpj).filter(c -> c.getPessoaCodigo() != pessoaCodigo).isPresent();
        if (deOutraPessoa) {
            throw RegraNegocioException.conflito("CNPJ já cadastrado para outra pessoa."); // RN-PES-06
        }
        fornecedor.ifPresent(f -> {
            f.setCnpj(cnpj);
            fornecedorRepository.save(f);
        });
        pj.ifPresent(c -> {
            c.setCnpj(cnpj);
            pessoaJuridicaRepository.save(c);
        });
        auditar("CORRIGIR_CNPJ", pessoaCodigo);
        return papeis(pessoaCodigo);
    }

    PessoaEntity buscarEntidade(int codigo) {
        return pessoaRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa não encontrada."));
    }

    // ------------------------------------------------------------------ validação

    private record DadosEndereco(String uf, String cidade, String cep, String logradouro, String nrImovel) {
    }

    private record DadosPessoa(String nome, String email, String telefone, DadosEndereco endereco) {
    }

    /** RN-PES-09 a RN-PES-14 e RN-END-05 a RN-END-10 (com a normalização da RN-SIS-06). */
    private DadosPessoa validar(PessoaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe os dados da pessoa: nome, contato e endereço.");
            v.verificar();
        }
        String nome = v.obrigatorio(dto.nome(), "Nome / razão social", 200); // RN-PES-09

        String email = Textos.minusculas(dto.email()); // RN-PES-11: minúsculas e sem espaços
        if (email != null && (email.length() > 150 || !Documentos.emailValido(email))) {
            v.erro("E-mail inválido ou já cadastrado."); // RN-PES-10
        }

        String telefone = null;
        if (Textos.limpar(dto.telefone()) != null) {
            telefone = Textos.somenteDigitos(dto.telefone()); // RN-PES-13: só dígitos, máscara só na tela
            if (telefone.length() > 20) {
                v.erro("Telefone: no máximo 20 caracteres."); // RN-PES-12
            } else if (!Documentos.telefoneValido(telefone)) {
                v.erro("Telefone inválido: informe DDD e número."); // RN-PES-13
            }
        }

        DadosEndereco endereco = validarEndereco(v, dto.endereco());
        v.verificar();
        return new DadosPessoa(nome, email, telefone, endereco);
    }

    private DadosEndereco validarEndereco(Validacao v, EnderecoRequest e) {
        if (e == null) {
            v.erro("Informe o endereço."); // RN-PES-14
            return null;
        }
        String uf = Textos.maiusculas(e.uf()); // RN-END-05: gravar em maiúsculas
        if (uf == null || !Documentos.UFS.contains(uf)) {
            v.erro("UF inválida."); // RN-END-05
        }
        String cidade = v.obrigatorio(e.cidade(), "Cidade", 60); // RN-END-06
        String cep = Textos.somenteDigitos(e.cep()); // RN-END-07: gravar sem máscara
        if (!Documentos.cepValido(cep)) {
            v.erro("CEP inválido: informe os 8 dígitos."); // RN-END-07
        }
        String logradouro = v.obrigatorio(e.logradouro(), "Logradouro", 100); // RN-END-08
        String nrImovel = v.opcional(e.nrImovel(), "Número do imóvel", 10); // RN-END-10 (aceita 'S/N')
        return new DadosEndereco(uf, cidade, cep, logradouro, nrImovel);
    }

    /** RN-END-02: endereço usado por mais de uma pessoa não é alterado no lugar; quem mudou recebe um novo. */
    private void atualizarEndereco(PessoaEntity pessoa, DadosEndereco d) {
        EnderecoEntity atual = pessoa.getEndereco();
        if (igual(atual, d)) {
            return;
        }
        if (pessoaRepository.countByEndereco(atual) > 1) {
            EnderecoEntity novo = new EnderecoEntity();
            preencher(novo, d);
            pessoa.setEndereco(enderecoRepository.save(novo));
        } else {
            preencher(atual, d);
            enderecoRepository.save(atual);
        }
    }

    private static boolean igual(EnderecoEntity e, DadosEndereco d) {
        return Objects.equals(e.getUf(), d.uf()) && Objects.equals(e.getCidade(), d.cidade())
                && Objects.equals(e.getCep(), d.cep()) && Objects.equals(e.getLogradouro(), d.logradouro())
                && Objects.equals(e.getNr_imovel(), d.nrImovel());
    }

    private static void preencher(EnderecoEntity e, DadosEndereco d) {
        e.setUf(d.uf());
        e.setCidade(d.cidade());
        e.setCep(d.cep());
        e.setLogradouro(d.logradouro());
        e.setNr_imovel(d.nrImovel());
    }

    static EnderecoResponse paraResponse(EnderecoEntity e) {
        return new EnderecoResponse(e.getCodigo(), e.getUf(), e.getCidade(), e.getCep(), e.getLogradouro(),
                e.getNr_imovel());
    }

    /** RN-SIS-13: registra quem fez a alteração sensível, sem dados pessoais no log. */
    private void auditar(String acao, int pessoaCodigo) {
        log.info("AUDITORIA login={} acao={} pessoa={}", usuarioLogado.login().orElse("-"), acao, pessoaCodigo);
    }
}
