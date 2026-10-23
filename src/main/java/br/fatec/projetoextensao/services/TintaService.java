package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.AjusteEstoqueRequest;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.entities.CorEntity;
import br.fatec.projetoextensao.entities.FornecedorEntity;
import br.fatec.projetoextensao.entities.TintaEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.TintaRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import br.fatec.projetoextensao.validacao.Valores;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): tintas e estoque (tabela Tintas).
 * Regras: RN-TIN-01 a RN-TIN-18, RN-FOR-03 (fornecedor inativo não é escolhido para tinta nova),
 * RN-SIS-03 (excluir: só ADMIN), RN-SIS-11 (valores em BigDecimal com 2 casas).
 *
 * RN-TIN-08 e RN-TIN-10 (listas de tipo e acabamento) ficam no front, como diz a planilha.
 * RN-TIN-16 (alerta de estoque mínimo) foi descartada na planilha.
 */
@Service
@RequiredArgsConstructor
public class TintaService {

    /** RN-TIN-15: o estoque de todas as tintas é registrado em kg. */
    public static final String UNIDADE_ESTOQUE = "kg";

    private final TintaRepository tintaRepository;
    private final ItemRepository itemRepository;
    private final FornecedorService fornecedorService;
    private final CorService corService;

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

    /**
     * Cadastro (RN-TIN-01 a RN-TIN-17). O estoque nasce 0 (RN-TIN-13) e só muda por ajuste (RN-TIN-14).
     * RN-FOR-03: o fornecedor precisa estar ativo.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public TintaResponse cadastrar(TintaRequest dto) {
        DadosTinta d = validar(dto);
        FornecedorEntity fornecedor = fornecedorService.buscarEntidade(dto.fornecedorCodigo()); // RN-TIN-04
        if (!fornecedor.isAtivo()) {
            throw new RegraNegocioException("Fornecedor inativo: escolha outro."); // RN-FOR-03
        }
        CorEntity cor = corService.buscarEntidade(dto.corCodigo()); // RN-TIN-05
        if (d.referencia() != null
                && tintaRepository.existsByFornecedorAndReferenciaFornecedorIgnoreCase(fornecedor, d.referencia())) {
            throw RegraNegocioException.conflito("Este fornecedor já tem tinta com essa referência."); // RN-TIN-12
        }
        TintaEntity t = new TintaEntity();
        t.setFornecedor(fornecedor);
        t.setCor(cor);
        aplicar(t, d);
        t.setEstoque(BigDecimal.ZERO.setScale(2)); // RN-TIN-13 e RN-SIS-10
        return paraResponse(tintaRepository.save(t));
    }

    /**
     * Alteração (mesmas validações do cadastro). A cor de uma tinta já usada em itens não muda, porque a tinta
     * aplicada precisa ter a cor pedida (RN-ITE-04). Trocar de fornecedor exige fornecedor ativo (RN-FOR-03).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public TintaResponse atualizar(int codigo, TintaRequest dto) {
        TintaEntity t = buscarEntidade(codigo);
        DadosTinta d = validar(dto);
        FornecedorEntity fornecedor = fornecedorService.buscarEntidade(dto.fornecedorCodigo());
        if (fornecedor.getPessoaCodigo() != t.getFornecedor().getPessoaCodigo() && !fornecedor.isAtivo()) {
            throw new RegraNegocioException("Fornecedor inativo: escolha outro."); // RN-FOR-03
        }
        CorEntity cor = corService.buscarEntidade(dto.corCodigo());
        if (cor.getCodigo() != t.getCor().getCodigo() && itemRepository.existsByTinta(t)) {
            throw new RegraNegocioException("Tinta já usada em itens: a cor não pode mudar."); // RN-ITE-04
        }
        if (d.referencia() != null && tintaRepository
                .existsByFornecedorAndReferenciaFornecedorIgnoreCaseAndCodigoNot(fornecedor, d.referencia(), codigo)) {
            throw RegraNegocioException.conflito("Este fornecedor já tem tinta com essa referência."); // RN-TIN-12
        }
        t.setFornecedor(fornecedor);
        t.setCor(cor);
        aplicar(t, d);
        return paraResponse(tintaRepository.save(t));
    }

    /**
     * RN-TIN-14: o estoque muda só por ajuste (entrada de compra = quantidade positiva; baixa = negativa), feito por
     * ADMIN ou OPERADOR, e o saldo nunca fica negativo. A linha da tinta é travada durante o ajuste, para dois
     * ajustes ao mesmo tempo não se perderem. Unidade: kg (RN-TIN-15).
     */
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
        t.setEstoque(novo);
        return paraResponse(tintaRepository.save(t));
    }

    /** RN-TIN-02: tinta usada em algum item não é excluída. RN-SIS-03: só ADMIN. */
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
            v.erro("Informe o fornecedor e a cor da tinta."); // RN-TIN-01
        }
        String descricao = v.obrigatorio(dto.descricao(), "Descrição da tinta", 100); // RN-TIN-06
        String tipo = v.opcional(dto.tipo(), "Tipo", 50); // RN-TIN-07
        String acabamento = v.opcional(dto.acabamento(), "Acabamento", 50); // RN-TIN-09
        String referencia = v.opcional(dto.referenciaFornecedor(), "Referência do fornecedor", 100); // RN-TIN-11
        // RN-TIN-17: obrigatório, >= 0 (CHECK ck_tintas_valor), 2 casas (decimal(18,2)).
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
        boolean precoAConfirmar = valor == null || valor.signum() == 0; // RN-TIN-18
        return new TintaResponse(t.getCodigo(), t.getFornecedor().getPessoaCodigo(),
                t.getFornecedor().getPessoa().getNome(), t.getFornecedor().isAtivo(), t.getCor().getCodigo(),
                t.getCor().getNome(), t.getDescricao(), t.getTipo(), t.getAcabamento(), t.getReferenciaFornecedor(),
                Valores.zeroSeNulo(t.getEstoque()), UNIDADE_ESTOQUE, valor, precoAConfirmar);
    }
}
