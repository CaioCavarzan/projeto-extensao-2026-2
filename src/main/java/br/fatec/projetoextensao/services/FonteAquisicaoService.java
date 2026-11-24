package br.fatec.projetoextensao.services;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ClienteRepository;
import br.fatec.projetoextensao.repositories.FonteAquisicaoRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * Fontes de aquisição de clientes (tabela FontesAquisicao).
 */

@Service
@RequiredArgsConstructor
public class FonteAquisicaoService {

    public static final String NAO_INFORMADA = "Não informada";

    private final FonteAquisicaoRepository fonteAquisicaoRepository;
    private final ClienteRepository clienteRepository;

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listar() {
        return fonteAquisicaoRepository.findAllByOrderByDescricao().stream()
                .map(f -> new ItemListaResponse(f.getCodigo(), f.getDescricao()))
                .toList();
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public ItemListaResponse cadastrar(NomeRequest dto) {
        String descricao = validarDescricao(dto);
        if (fonteAquisicaoRepository.existsByDescricaoIgnoreCase(descricao)) {
            throw RegraNegocioException.conflito("Esta fonte de aquisição já existe.");
        }
        FonteAquisicaoEntity fonte = new FonteAquisicaoEntity();
        fonte.setDescricao(descricao);
        fonte = fonteAquisicaoRepository.save(fonte);
        return new ItemListaResponse(fonte.getCodigo(), fonte.getDescricao());
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public ItemListaResponse renomear(int codigo, NomeRequest dto) {
        FonteAquisicaoEntity fonte = buscarEntidade(codigo);
        protegerNaoInformada(fonte);
        String descricao = validarDescricao(dto);
        if (fonteAquisicaoRepository.existsByDescricaoIgnoreCaseAndCodigoNot(descricao, codigo)) {
            throw RegraNegocioException.conflito("Esta fonte de aquisição já existe.");
        }
        fonte.setDescricao(descricao);
        return new ItemListaResponse(fonte.getCodigo(), fonteAquisicaoRepository.save(fonte).getDescricao());
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        FonteAquisicaoEntity fonte = buscarEntidade(codigo);
        protegerNaoInformada(fonte);
        if (clienteRepository.existsByFonte(fonte)) {
            throw RegraNegocioException.conflito("Fonte em uso por clientes não pode ser excluída.");
        }
        fonteAquisicaoRepository.delete(fonte);
    }

    FonteAquisicaoEntity buscarEntidade(int codigo) {
        return fonteAquisicaoRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fonte de aquisição não encontrada.")); 
    }

    FonteAquisicaoEntity naoInformada() {
        return fonteAquisicaoRepository.findByDescricaoIgnoreCase(NAO_INFORMADA)
                .orElseThrow(() -> new IllegalStateException("Fonte '" + NAO_INFORMADA + "' não cadastrada."));
    }

    private void protegerNaoInformada(FonteAquisicaoEntity fonte) {
        if (NAO_INFORMADA.equalsIgnoreCase(fonte.getDescricao())) {
            throw new RegraNegocioException("Esta fonte é usada pelo sistema e não pode ser alterada."); 
        }
    }

    private String validarDescricao(NomeRequest dto) {
        Validacao v = new Validacao();
        String descricao = v.obrigatorio(dto == null ? null : dto.nome(), "Fonte de aquisição", 50);
        v.verificar();
        return descricao;
    }
}
