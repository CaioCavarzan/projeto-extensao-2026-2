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
 * Fornecedores (tabela Fornecedores).
 */

@Service
@RequiredArgsConstructor
public class FornecedorService {

    private static final Logger log = LoggerFactory.getLogger(FornecedorService.class);

    public static final List<String> PRAZOS_PAGAMENTO = List.of("À vista", "30 dias", "30/60/90 dias");

    private final FornecedorRepository fornecedorRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final TintaRepository tintaRepository;
    private final PessoaService pessoaService;
    private final UsuarioLogado usuarioLogado;

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public FornecedorResponse cadastrar(FornecedorRequest dto) {
        if (dto == null) {
            throw new RegraNegocioException("Informe os dados do fornecedor.");
        }
        Validacao v = new Validacao();
        String cnpj = Documentos.normalizarCnpj(dto.cnpj()); 
        if (cnpj == null || cnpj.isEmpty()) {
            v.erro("Informe o CNPJ do fornecedor."); 
        } else if (!cnpj.matches("[0-9A-Z]{12}\\d{2}")) {
            v.erro("CNPJ inválido ou já cadastrado."); 
        } else if (!Documentos.cnpjValido(cnpj)) {
            v.erro("CNPJ inválido."); 
        }
        String prazo = validarPrazo(v, dto.prazoPagamento());
        v.verificar();

        if (fornecedorRepository.existsByCnpj(cnpj)) {
            throw RegraNegocioException.conflito("CNPJ inválido ou já cadastrado."); 
        }
        List<String> avisos = new ArrayList<>();
        PessoaEntity pessoa;
        Optional<PessoaEntity> existente = pessoaService.buscarPorCnpj(cnpj);
        if (existente.isPresent()) {
            pessoa = existente.get();
            if (funcionarioRepository.existsById(pessoa.getCodigo())) {
                throw RegraNegocioException.conflito(
                        "Uma pessoa não pode ser funcionário e fornecedor; cadastre a empresa como outra pessoa."); 
            }
            avisos.add("Esta pessoa já está cadastrada como cliente; o novo papel foi ligado a ela.");
        } else {
            pessoa = pessoaService.criarPessoa(dto.pessoa()); 
        }

        FornecedorEntity f = new FornecedorEntity();
        f.setPessoa(pessoa); 
        f.setCnpj(cnpj);
        f.setPrazoPagamento(prazo);
        f.setAtivo(true); 
        f = fornecedorRepository.save(f);
        return paraResponse(f, avisos);
    }

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
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado.")); 
    }

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
