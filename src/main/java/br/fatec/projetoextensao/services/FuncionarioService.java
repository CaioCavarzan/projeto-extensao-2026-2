package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.FuncionarioAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.FuncionarioRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.entities.FuncionarioEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.FornecedorRepository;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.UsuarioRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Documentos;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): funcionários (tabela Funcionarios).
 * Regras: RN-FUN-01 a RN-FUN-11, RN-PES-02 (o funcionário que já é cliente PF reaproveita a mesma pessoa),
 * RN-PES-05 (funcionário nunca é fornecedor), RN-SIS-03 (salário e exclusão: só ADMIN).
 *
 * O cadastro é só do ADMIN porque o salário é obrigatório (NOT NULL) e só o ADMIN vê e altera salário (RN-FUN-09).
 */
@Service
@RequiredArgsConstructor
public class FuncionarioService {

    private static final Logger log = LoggerFactory.getLogger(FuncionarioService.class);

    /** RN-FUN-07: lista padronizada de cargos (definida pelo grupo na planilha). */
    public static final List<String> CARGOS = List.of("Jatista", "Pintor", "Auxiliar de Produção", "Secretário",
            "Auxiliar Administrativo");

    private final FuncionarioRepository funcionarioRepository;
    private final FornecedorRepository fornecedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;
    private final PessoaService pessoaService;
    private final UsuarioService usuarioService;
    private final UsuarioLogado usuarioLogado;

    /**
     * Cadastro de funcionário, com a pessoa na mesma transação (RN-PES-01, RN-SIS-04). Se o CPF já pertence a um
     * cliente PF, o funcionário é ligado à mesma pessoa (RN-PES-02 e RN-PES-03).
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public FuncionarioResponse cadastrar(FuncionarioRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do funcionário.");
        }
        Validacao v = new Validacao();
        String cpf = Documentos.normalizarCpf(dto.cpf());
        if (cpf == null || cpf.isEmpty()) {
            v.erro("Informe o CPF do funcionário."); // RN-FUN-01
        } else if (!cpf.matches("\\d{11}")) {
            v.erro("CPF inválido ou já cadastrado."); // RN-FUN-04 (formato do CHECK)
        } else if (!Documentos.cpfValido(cpf)) {
            v.erro("CPF inválido."); // RN-FUN-05
        }
        DadosFuncionario df = validarDados(v, dto.cargo(), dto.salario(), dto.dataAdmissao());
        v.verificar();

        if (funcionarioRepository.existsByCpf(cpf)) {
            throw RegraNegocioException.conflito("CPF inválido ou já cadastrado."); // RN-FUN-04
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCpf(cpf);
        if (existente.isPresent()) {
            // RN-PES-02: o CPF é de um cliente PF -> o papel de funcionário reaproveita a mesma pessoa.
            pessoa = existente.get();
            garantirQueNaoEFornecedor(pessoa); // RN-PES-05
            avisos.add("Esta pessoa já está cadastrada como cliente; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); // RN-PES-01
        }

        FuncionarioEntity f = new FuncionarioEntity();
        f.setPessoa(pessoa); // RN-FUN-03: a chave é o código da pessoa (@MapsId)
        f.setCpf(cpf);
        f.setCargo(df.cargo());
        f.setSalario(df.salario());
        f.setDataAdmissao(df.dataAdmissao());
        f = funcionarioRepository.save(f);
        auditar("CADASTRAR_FUNCIONARIO", f.getPessoaCodigo());
        return paraResponse(f, avisos);
    }

    /** Alteração de dados (só ADMIN, porque inclui o salário: RN-FUN-09). O CPF é corrigido em PessoaService. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public FuncionarioResponse atualizar(int codigo, FuncionarioAtualizacaoRequest dto) {
        FuncionarioEntity f = buscarEntidade(codigo);
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do funcionário.");
        }
        Validacao v = new Validacao();
        DadosFuncionario df = validarDados(v, dto.cargo(), dto.salario(), dto.dataAdmissao());
        v.verificar();
        pessoaService.atualizarPessoa(f.getPessoa(), dto.pessoa());
        boolean mudouSalario = f.getSalario() == null || f.getSalario().compareTo(df.salario()) != 0;
        f.setCargo(df.cargo());
        f.setSalario(df.salario());
        f.setDataAdmissao(df.dataAdmissao());
        f = funcionarioRepository.save(f);
        if (mudouSalario) {
            auditar("ALTERAR_SALARIO", codigo); // RN-SIS-13: registra que mudou, nunca o valor (RN-FUN-09)
        }
        return paraResponse(f, List.of());
    }

    /** RN-FUN-09: a listagem só traz o salário para ADMIN. */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<FuncionarioResponse> listar() {
        return funcionarioRepository.findAllByOrderByPessoaNome().stream()
                .map(f -> paraResponse(f, List.of()))
                .toList();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public FuncionarioResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo), List.of());
    }

    /**
     * RN-FUN-02: desligamento = bloquear o usuário (Usuarios.ativo = 0); o cadastro fica para o histórico.
     * RN-USU-06 vale aqui também: o último ADMIN ativo não é bloqueado.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public FuncionarioResponse desligar(int codigo) {
        FuncionarioEntity f = buscarEntidade(codigo);
        if (usuarioRepository.existsById(codigo)) {
            usuarioService.bloquear(codigo);
        }
        auditar("DESLIGAR_FUNCIONARIO", codigo);
        return paraResponse(f, List.of());
    }

    /**
     * RN-FUN-02: funcionário com usuário (e, portanto, com OS ou cronograma registrados) ou com etapa operada não é
     * excluído. RN-SIS-03: só ADMIN. RN-PES-07: a pessoa sem outro papel também é excluída.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        FuncionarioEntity f = buscarEntidade(codigo);
        if (usuarioRepository.existsById(codigo) || itemRotaProcessoRepository.existsByFuncionario(f)) {
            throw RegraNegocioException.conflito("Funcionário com registros não pode ser excluído; bloqueie o acesso.");
        }
        PessoaEntity pessoa = f.getPessoa();
        funcionarioRepository.delete(f);
        funcionarioRepository.flush();
        pessoaService.excluirSeSemPapel(pessoa);
        auditar("EXCLUIR_FUNCIONARIO", codigo);
    }

    FuncionarioEntity buscarEntidade(int codigo) {
        return funcionarioRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionário não encontrado.")); // RN-IRP-16
    }

    // ------------------------------------------------------------------ apoio

    private record DadosFuncionario(String cargo, BigDecimal salario, LocalDate dataAdmissao) {
    }

    private DadosFuncionario validarDados(Validacao v, String cargoInformado, BigDecimal salarioInformado,
                                          LocalDate dataAdmissao) {
        String cargo = v.obrigatorio(cargoInformado, "Cargo", 50); // RN-FUN-06
        if (cargo != null) {
            String padrao = CARGOS.stream().filter(c -> c.equalsIgnoreCase(cargo)).findFirst().orElse(null);
            if (padrao == null) {
                v.erro("Cargo inválido."); // RN-FUN-07
            } else {
                cargoInformado = padrao; // grava com a grafia da lista
            }
        }
        // RN-FUN-08: obrigatório, >= 0 (CHECK) e com 2 casas (decimal(18,2)).
        BigDecimal salario = v.valor(salarioInformado, true, BigDecimal.ZERO, true, Valores.DIGITOS_DECIMAL_18_2,
                "Salário inválido.");
        if (dataAdmissao == null) {
            v.erro("Informe a data de admissão."); // RN-FUN-10
        } else if (dataAdmissao.isAfter(LocalDate.now())) {
            v.erro("A data de admissão não pode estar no futuro."); // RN-FUN-11
        }
        return new DadosFuncionario(cargo == null ? null : cargoInformado, salario, dataAdmissao);
    }

    /** RN-PES-05: uma pessoa não é funcionário e fornecedor ao mesmo tempo. */
    private void garantirQueNaoEFornecedor(PessoaEntity pessoa) {
        if (fornecedorRepository.existsById(pessoa.getCodigo())) {
            throw RegraNegocioException.conflito(
                    "Uma pessoa não pode ser funcionário e fornecedor; cadastre a empresa como outra pessoa.");
        }
    }

    private FuncionarioResponse paraResponse(FuncionarioEntity f, List<String> avisos) {
        PessoaEntity p = f.getPessoa();
        Optional<UsuarioEntity> usuario = usuarioRepository.findById(f.getPessoaCodigo());
        BigDecimal salario = usuarioLogado.isAdmin() ? f.getSalario() : null; // RN-FUN-09
        return new FuncionarioResponse(f.getPessoaCodigo(), p.getNome(), p.getEmail(), p.getTelefone(),
                PessoaService.paraResponse(p.getEndereco()), f.getCpf(), f.getCargo(), salario, f.getDataAdmissao(),
                usuario.isPresent(), usuario.map(UsuarioEntity::isAtivo).orElse(null),
                pessoaService.papeis(f.getPessoaCodigo()), avisos);
    }

    /** RN-SIS-13: log sem dados pessoais (o salário nunca aparece: RN-FUN-09). */
    private void auditar(String acao, int funcionarioCodigo) {
        log.info("AUDITORIA login={} acao={} funcionario={}", usuarioLogado.login().orElse("-"), acao, funcionarioCodigo);
    }
}
