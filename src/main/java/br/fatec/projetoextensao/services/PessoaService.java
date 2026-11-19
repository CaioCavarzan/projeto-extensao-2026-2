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
 * Concentra as regras de Pessoa e Endereço e a regra de especialização do grupo:
 * <ul>
 *   <li>toda pessoa é obrigatoriamente cliente, funcionário ou fornecedor: não existe cadastro de pessoa avulsa. 
 *       A pessoa nasce junto com o papel, na mesma transação, e é excluída quando perde o último papel;</li>
 *   <li>a pessoa pode ser cliente e funcionário, ou cliente e fornecedor, ao mesmo tempo: o papel novo reaproveita
 *       a pessoa encontrada pelo CPF ou CNPJ;</li>
 *   <li>funcionário nunca é fornecedor, e vice-versa;</li>
 *   <li>o mesmo CPF (ou CNPJ) não pertence a duas pessoas diferentes.</li>
 * </ul>
 * Também valida os dados da pessoa e do endereço.
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

    PessoaEntity criarPessoa(PessoaRequest dto) {
        DadosPessoa d = validar(dto);
        if (d.email() != null && pessoaRepository.existsByEmailIgnoreCase(d.email())) {
            throw RegraNegocioException.conflito("E-mail inválido ou já cadastrado."); 
        }
        EnderecoEntity endereco = new EnderecoEntity();
        preencher(endereco, d.endereco());
        endereco = enderecoRepository.save(endereco);

        PessoaEntity pessoa = new PessoaEntity();
        pessoa.setNome(d.nome());
        pessoa.setEmail(d.email());
        pessoa.setTelefone(d.telefone());
        pessoa.setEndereco(endereco); 
        return pessoaRepository.save(pessoa);
    }

    void atualizarPessoa(PessoaEntity pessoa, PessoaRequest dto) {
        DadosPessoa d = validar(dto);
        if (d.email() != null && pessoaRepository.existsByEmailIgnoreCaseAndCodigoNot(d.email(), pessoa.getCodigo())) {
            throw RegraNegocioException.conflito("E-mail inválido ou já cadastrado."); 
        }
        pessoa.setNome(d.nome());
        pessoa.setEmail(d.email());
        pessoa.setTelefone(d.telefone());
        atualizarEndereco(pessoa, d.endereco());
        pessoaRepository.save(pessoa);
    }

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

    Optional<PessoaEntity> buscarPorCpf(String cpf) {
        Optional<FuncionarioEntity> funcionario = funcionarioRepository.findByCpf(cpf);
        if (funcionario.isPresent()) {
            return Optional.of(funcionario.get().getPessoa());
        }
        return pessoaFisicaRepository.findByCpf(cpf).map(ClienteEntity::getPessoa);
    }

    Optional<PessoaEntity> buscarPorCnpj(String cnpj) {
        Optional<FornecedorEntity> fornecedor = fornecedorRepository.findByCnpj(cnpj);
        if (fornecedor.isPresent()) {
            return Optional.of(fornecedor.get().getPessoa());
        }
        return pessoaJuridicaRepository.findByCnpj(cnpj).map(ClienteEntity::getPessoa);
    }

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

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public List<String> corrigirCpf(int pessoaCodigo, String novoCpf) {
        buscarEntidade(pessoaCodigo);
        String cpf = Documentos.normalizarCpf(novoCpf);
        if (!Documentos.cpfValido(cpf)) {
            throw new RegraNegocioException("CPF inválido."); 
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
            throw RegraNegocioException.conflito("CPF já cadastrado para outra pessoa."); 
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

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public List<String> corrigirCnpj(int pessoaCodigo, String novoCnpj) {
        buscarEntidade(pessoaCodigo);
        String cnpj = Documentos.normalizarCnpj(novoCnpj);
        if (!Documentos.cnpjValido(cnpj)) {
            throw new RegraNegocioException("CNPJ inválido."); 
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
            throw RegraNegocioException.conflito("CNPJ já cadastrado para outra pessoa."); 
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


    private DadosPessoa validar(PessoaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe os dados da pessoa: nome, contato e endereço.");
            v.verificar();
        }
        String nome = v.obrigatorio(dto.nome(), "Nome / razão social", 200); 

        String email = Textos.minusculas(dto.email()); 
        if (email != null && (email.length() > 150 || !Documentos.emailValido(email))) {
            v.erro("E-mail inválido ou já cadastrado."); 
        }

        String telefone = null;
        if (Textos.limpar(dto.telefone()) != null) {
            telefone = Textos.somenteDigitos(dto.telefone()); 
            if (telefone.length() > 20) {
                v.erro("Telefone: no máximo 20 caracteres."); 
            } else if (!Documentos.telefoneValido(telefone)) {
                v.erro("Telefone inválido: informe DDD e número."); 
            }
        }

        DadosEndereco endereco = validarEndereco(v, dto.endereco());
        v.verificar();
        return new DadosPessoa(nome, email, telefone, endereco);
    }

    private DadosEndereco validarEndereco(Validacao v, EnderecoRequest e) {
        if (e == null) {
            v.erro("Informe o endereço."); 
            return null;
        }
        String uf = Textos.maiusculas(e.uf()); 
        if (uf == null || !Documentos.UFS.contains(uf)) {
            v.erro("UF inválida."); 
        }
        String cidade = v.obrigatorio(e.cidade(), "Cidade", 60); 
        String cep = Textos.somenteDigitos(e.cep()); 
        if (!Documentos.cepValido(cep)) {
            v.erro("CEP inválido: informe os 8 dígitos."); 
        }
        String logradouro = v.obrigatorio(e.logradouro(), "Logradouro", 100); 
        String nrImovel = v.opcional(e.nrImovel(), "Número do imóvel", 10); 
        return new DadosEndereco(uf, cidade, cep, logradouro, nrImovel);
    }

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

    private void auditar(String acao, int pessoaCodigo) {
        log.info("AUDITORIA login={} acao={} pessoa={}", usuarioLogado.login().orElse("-"), acao, pessoaCodigo);
    }
}
