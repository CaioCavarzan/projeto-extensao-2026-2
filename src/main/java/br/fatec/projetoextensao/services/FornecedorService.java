package br.fatec.projetoextensao.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.FornecedorAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.FornecedorRequest;
import br.fatec.projetoextensao.dtos.FornecedorResponse;
import br.fatec.projetoextensao.entities.FornecedorEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.FornecedorRepository;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.TintaRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Documentos;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): fornecedores (tabela Fornecedores).
 * Regras: RN-FOR-01 a RN-FOR-09, RN-PES-02 (o fornecedor que já é cliente PJ reaproveita a mesma pessoa),
 * RN-PES-05 (fornecedor nunca é funcionário), RN-SIS-03 (inativar e excluir: só ADMIN).
 */
@Service
@RequiredArgsConstructor
public class FornecedorService {

    private static final Logger log = LoggerFactory.getLogger(FornecedorService.class);

    /** RN-FOR-09: lista padronizada de prazos de pagamento (definida pelo grupo na planilha). */
    public static final List<String> PRAZOS_PAGAMENTO = List.of("À vista", "30 dias", "30/60/90 dias");

    private final FornecedorRepository fornecedorRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final TintaRepository tintaRepository;
    private final PessoaService pessoaService;
    private final UsuarioLogado usuarioLogado;

    /**
     * Cadastro de fornecedor, com a pessoa na mesma transação (RN-PES-01, RN-SIS-04). Se o CNPJ já pertence a um
     * cliente PJ, o fornecedor é ligado à mesma pessoa (RN-PES-02 e RN-PES-04). Nasce ativo (RN-FOR-07).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public FornecedorResponse cadastrar(FornecedorRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do fornecedor.");
        }
        Validacao v = new Validacao();
        String cnpj = Documentos.normalizarCnpj(dto.cnpj()); // RN-FOR-05: sem máscara e em maiúsculas
        if (cnpj == null || cnpj.isEmpty()) {
            v.erro("Informe o CNPJ do fornecedor."); // RN-FOR-01
        } else if (!cnpj.matches("[0-9A-Z]{12}\\d{2}")) {
            v.erro("CNPJ inválido ou já cadastrado."); // RN-FOR-05 (formato do CHECK)
        } else if (!Documentos.cnpjValido(cnpj)) {
            v.erro("CNPJ inválido."); // RN-FOR-06
        }
        String prazo = validarPrazo(v, dto.prazoPagamento());
        v.verificar();

        if (fornecedorRepository.existsByCnpj(cnpj)) {
            throw RegraNegocioException.conflito("CNPJ inválido ou já cadastrado."); // RN-FOR-05
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCnpj(cnpj);
        if (existente.isPresent()) {
            // RN-PES-02: o CNPJ é de um cliente PJ -> o papel de fornecedor reaproveita a mesma pessoa.
            pessoa = existente.get();
            if (funcionarioRepository.existsById(pessoa.getCodigo())) {
                throw RegraNegocioException.conflito(
                        "Uma pessoa não pode ser funcionário e fornecedor; cadastre a empresa como outra pessoa."); // RN-PES-05
            }
            avisos.add("Esta pessoa já está cadastrada como cliente; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); // RN-PES-01
        }

        FornecedorEntity f = new FornecedorEntity();
        f.setPessoa(pessoa); // RN-FOR-04: a chave é o código da pessoa (@MapsId)
        f.setCnpj(cnpj);
        f.setPrazoPagamento(prazo);
        f.setAtivo(true); // RN-FOR-07 e RN-SIS-10
        f = fornecedorRepository.save(f);
        return paraResponse(f, avisos);
    }

    /** Alteração dos dados da pessoa e do prazo. O CNPJ é corrigido em PessoaService.corrigirCnpj (só ADMIN). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public FornecedorResponse atualizar(int codigo, FornecedorAtualizacaoRequest dto) {
        FornecedorEntity f = buscarEntidade(codigo);
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do fornecedor.");
        }
        Validacao v = new Validacao();
        String prazo = validarPrazo(v, dto.prazoPagamento());
        v.verificar();
        pessoaService.atualizarPessoa(f.getPessoa(), dto.pessoa());
        f.setPrazoPagamento(prazo);
        return paraResponse(fornecedorRepository.save(f), List.of());
    }

    /** RN-FOR-02: fornecedor com tintas é inativado em vez de excluído. RN-SIS-03: só ADMIN. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public FornecedorResponse inativar(int codigo) {
        FornecedorEntity f = buscarEntidade(codigo);
        f.setAtivo(false);
        log.info("AUDITORIA login={} acao=INATIVAR_FORNECEDOR fornecedor={}", usuarioLogado.login().orElse("-"), codigo);
        return paraResponse(fornecedorRepository.save(f), List.of());
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public FornecedorResponse reativar(int codigo) {
        FornecedorEntity f = buscarEntidade(codigo);
        f.setAtivo(true);
        return paraResponse(fornecedorRepository.save(f), List.of());
    }

    /**
     * RN-FOR-02: fornecedor com tintas não é excluído (é inativado). RN-SIS-03: só ADMIN.
     * RN-PES-07: a pessoa sem outro papel também é excluída.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        FornecedorEntity f = buscarEntidade(codigo);
        if (tintaRepository.existsByFornecedor(f)) {
            throw RegraNegocioException.conflito("Fornecedor com tintas não pode ser excluído; inative-o.");
        }
        PessoaEntity pessoa = f.getPessoa();
        fornecedorRepository.delete(f);
        fornecedorRepository.flush();
        pessoaService.excluirSeSemPapel(pessoa);
    }

    /** somenteAtivos = true: lista para escolher fornecedor de tinta nova (RN-FOR-03). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<FornecedorResponse> listar(boolean somenteAtivos) {
        List<FornecedorEntity> lista = somenteAtivos
                ? fornecedorRepository.findByAtivoTrueOrderByPessoaNome()
                : fornecedorRepository.findAllByOrderByPessoaNome();
        return lista.stream().map(f -> paraResponse(f, List.of())).toList();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public FornecedorResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo), List.of());
    }

    FornecedorEntity buscarEntidade(int codigo) {
        return fornecedorRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado.")); // RN-TIN-04
    }

    /** RN-FOR-08 (obrigatório, até 50) e RN-FOR-09 (lista padronizada; gravado com a grafia da lista). */
    private String validarPrazo(Validacao v, String valor) {
        String prazo = v.obrigatorio(valor, "Prazo de pagamento", 50);
        if (prazo == null) {
            return null;
        }
        String padrao = PRAZOS_PAGAMENTO.stream().filter(p -> p.equalsIgnoreCase(prazo)).findFirst().orElse(null);
        if (padrao == null) {
            v.erro("Prazo de pagamento inválido: escolha " + String.join(", ", PRAZOS_PAGAMENTO) + ".");
        }
        return padrao;
    }

    private FornecedorResponse paraResponse(FornecedorEntity f, List<String> avisos) {
        PessoaEntity p = f.getPessoa();
        return new FornecedorResponse(f.getPessoaCodigo(), p.getNome(), p.getEmail(), p.getTelefone(),
                PessoaService.paraResponse(p.getEndereco()), f.getCnpj(), f.getPrazoPagamento(), f.isAtivo(),
                pessoaService.papeis(f.getPessoaCodigo()), avisos);
    }
}
