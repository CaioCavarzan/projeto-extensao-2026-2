package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.AjusteEstoqueRequest;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.entities.CorEntity;
import br.fatec.projetoextensao.entities.FornecedorEntity;
import br.fatec.projetoextensao.entities.TintaEntity;
import br.fatec.projetoextensao.exceptions.ErrosBanco;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.ProcedimentosBanco;
import br.fatec.projetoextensao.repositories.TintaRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Tintas e estoque (tabela Tintas).
 */

@Service
@RequiredArgsConstructor
public class TintaService {

    private static final Logger log = LoggerFactory.getLogger(TintaService.class);

    public static final String UNIDADE_ESTOQUE = "kg";

    private final TintaRepository tintaRepository;
    private final ItemRepository itemRepository;
    private final FornecedorService fornecedorService;
    private final CorService corService;
    private final ProcedimentosBanco procedimentosBanco;
    private final EntityManager entityManager;

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<TintaResponse> listar() {
        return tintaRepository.findAllByOrderByDescricao().stream().map(this::paraResponse).toList();
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public TintaResponse buscar(int codigo) {
        return paraResponse(buscarEntidade(codigo));
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public TintaResponse cadastrar(TintaRequest dto) {
        DadosTinta d = validar(dto);
        FornecedorEntity fornecedor = fornecedorService.buscarEntidade(dto.fornecedorCodigo()); 
        if (!fornecedor.isAtivo()) {
            throw new RegraNegocioException("Fornecedor inativo: escolha outro."); 
        }
        CorEntity cor = corService.buscarEntidade(dto.corCodigo()); 
        if (d.referencia() != null
                && tintaRepository.existsByFornecedorAndReferenciaFornecedorIgnoreCase(fornecedor, d.referencia())) {
            throw RegraNegocioException.conflito("Este fornecedor já tem tinta com essa referência."); 
        }
        TintaEntity t = new TintaEntity();
        t.setFornecedor(fornecedor);
        t.setCor(cor);
        aplicar(t, d);
        return paraResponse(tintaRepository.save(t));
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public TintaResponse atualizar(int codigo, TintaRequest dto) {
        TintaEntity t = buscarEntidade(codigo);
        DadosTinta d = validar(dto);
        FornecedorEntity fornecedor = fornecedorService.buscarEntidade(dto.fornecedorCodigo());
        if (fornecedor.getPessoaCodigo() != t.getFornecedor().getPessoaCodigo() && !fornecedor.isAtivo()) {
            throw new RegraNegocioException("Fornecedor inativo: escolha outro."); 
        }
        CorEntity cor = corService.buscarEntidade(dto.corCodigo());
        if (cor.getCodigo() != t.getCor().getCodigo() && itemRepository.existsByTinta(t)) {
            throw new RegraNegocioException("Tinta já usada em itens: a cor não pode mudar."); 
        }
        if (d.referencia() != null && tintaRepository
                .existsByFornecedorAndReferenciaFornecedorIgnoreCaseAndCodigoNot(fornecedor, d.referencia(), codigo)) {
            throw RegraNegocioException.conflito("Este fornecedor já tem tinta com essa referência."); 
        }
        t.setFornecedor(fornecedor);
        t.setCor(cor);
        aplicar(t, d);
        return paraResponse(tintaRepository.save(t));
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public TintaResponse ajustarEstoque(int codigo, AjusteEstoqueRequest dto) {
        Validacao v = new Validacao();
        BigDecimal quantidade = dto == null ? null : dto.quantidade();
        if (quantidade == null || quantidade.signum() == 0
                || !Valores.casasValidas(quantidade.abs(), Valores.DIGITOS_DECIMAL_10_2)) {
            v.erro("Informe a quantidade do ajuste (positiva para entrada, negativa para baixa), com até 2 casas.");
        }
        v.verificar();
        TintaEntity t = tintaRepository.buscarParaAtualizar(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tinta não encontrada."));
        BigDecimal atual = Valores.zeroSeNulo(t.getEstoque());
        BigDecimal novo = Valores.normalizar(atual.add(quantidade));
        if (novo.signum() < 0) {
            throw new RegraNegocioException("Estoque insuficiente: saldo atual " + atual.toPlainString() + " "
                    + UNIDADE_ESTOQUE + "."); // RN-TIN-14
        }
        if (!Valores.casasValidas(novo, Valores.DIGITOS_DECIMAL_10_2)) {
            throw new RegraNegocioException("Estoque acima do limite da coluna (decimal(10,2)).");
        }
        BigDecimal saldoDoBanco;
        try {
            saldoDoBanco = procedimentosBanco.ajustarEstoqueTinta(codigo, Valores.normalizar(quantidade));
        } catch (DataAccessException e) {
            throw ErrosBanco.converter(e);
        }
        entityManager.refresh(t); 
        if (saldoDoBanco == null || saldoDoBanco.compareTo(Valores.zeroSeNulo(t.getEstoque())) != 0) {
            log.warn("Tinta {}: @estoque_resultante da procedure ({}) difere do estoque_atual gravado ({}).", codigo,
                    saldoDoBanco, t.getEstoque());
        }
        return paraResponse(t);
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        TintaEntity t = buscarEntidade(codigo);
        if (itemRepository.existsByTinta(t)) {
            throw RegraNegocioException.conflito("Tinta em uso não pode ser excluída.");
        }
        tintaRepository.delete(t);
    }

    TintaEntity buscarEntidade(int codigo) {
        return tintaRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tinta não encontrada."));
    }

    // ------------------------------------------------------------------ apoio

    private record DadosTinta(String descricao, String tipo, String acabamento, String referencia, BigDecimal valor) {
    }

    private DadosTinta validar(TintaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe os dados da tinta.");
            v.verificar();
        }
        if (dto.fornecedorCodigo() == null || dto.corCodigo() == null) {
            v.erro("Informe o fornecedor e a cor da tinta."); 
        }
        String descricao = v.obrigatorio(dto.descricao(), "Descrição da tinta", 100); 
        String tipo = v.opcional(dto.tipo(), "Tipo", 50); 
        String acabamento = v.opcional(dto.acabamento(), "Acabamento", 50); 
        String referencia = v.opcional(dto.referenciaFornecedor(), "Referência do fornecedor", 100); 
        BigDecimal valor = v.valor(dto.valorUnitario(), true, BigDecimal.ZERO, true, Valores.DIGITOS_DECIMAL_18_2,
                "Valor unitário inválido.");
        v.verificar();
        return new DadosTinta(descricao, tipo, acabamento, referencia, valor);
    }

    private void aplicar(TintaEntity t, DadosTinta d) {
        t.setDescricao(d.descricao());
        t.setTipo(d.tipo());
        t.setAcabamento(d.acabamento());
        t.setReferenciaFornecedor(d.referencia());
        t.setValorUnitario(d.valor());
    }

    private TintaResponse paraResponse(TintaEntity t) {
        BigDecimal valor = t.getValorUnitario();
        boolean precoAConfirmar = valor == null || valor.signum() == 0; 
        return new TintaResponse(t.getCodigo(), t.getFornecedor().getPessoaCodigo(),
                t.getFornecedor().getPessoa().getNome(), t.getFornecedor().isAtivo(), t.getCor().getCodigo(),
                t.getCor().getNome(), t.getDescricao(), t.getTipo(), t.getAcabamento(), t.getReferenciaFornecedor(),
                Valores.zeroSeNulo(t.getEstoque()), UNIDADE_ESTOQUE, valor, precoAConfirmar);
    }
}
