package br.fatec.projetoextensao.services;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.entities.CorEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.CorRepository;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.TintaRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): cores (tabela Cores).
 * Regras: RN-COR-01 (cor em uso não é excluída), RN-COR-02 e RN-COR-03 (nome obrigatório, até 50 caracteres e
 * único), RN-SIS-03 (alterar listas de apoio: só ADMIN).
 *
 * RN-COR-04 (nome padronizado, ex.: com o código RAL) não tem um formato definido na planilha; a unicidade sem
 * diferenciar maiúsculas já evita 'Azul' e 'azul' como cores diferentes.
 */
@Service
@RequiredArgsConstructor
public class CorService {

    private final CorRepository corRepository;
    private final TintaRepository tintaRepository;
    private final ItemRepository itemRepository;

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listar() {
        return corRepository.findAllByOrderByNome().stream()
                .map(c -> new ItemListaResponse(c.getCodigo(), c.getNome()))
                .toList();
    }

    /** RN-COR-03: nome obrigatório, até 50 caracteres e único. RN-SIS-03: só ADMIN. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public ItemListaResponse cadastrar(NomeRequest dto) {
        String nome = validarNome(dto);
        if (corRepository.existsByNomeIgnoreCase(nome)) {
            throw RegraNegocioException.conflito("Esta cor já está cadastrada.");
        }
        CorEntity cor = new CorEntity();
        cor.setNome(nome);
        cor = corRepository.save(cor);
        return new ItemListaResponse(cor.getCodigo(), cor.getNome());
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public ItemListaResponse renomear(int codigo, NomeRequest dto) {
        CorEntity cor = buscarEntidade(codigo);
        String nome = validarNome(dto);
        if (corRepository.existsByNomeIgnoreCaseAndCodigoNot(nome, codigo)) {
            throw RegraNegocioException.conflito("Esta cor já está cadastrada.");
        }
        cor.setNome(nome);
        return new ItemListaResponse(cor.getCodigo(), corRepository.save(cor).getNome());
    }

    /** RN-COR-01: cor usada em tinta ou em item não é excluída. RN-SIS-03: só ADMIN. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        CorEntity cor = buscarEntidade(codigo);
        if (tintaRepository.existsByCor(cor) || itemRepository.existsByCor(cor)) {
            throw RegraNegocioException.conflito("Cor em uso não pode ser excluída.");
        }
        corRepository.delete(cor);
    }

    CorEntity buscarEntidade(int codigo) {
        return corRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cor não encontrada.")); // RN-TIN-05, RN-ITE-13
    }

    private String validarNome(NomeRequest dto) {
        Validacao v = new Validacao();
        String nome = v.obrigatorio(dto == null ? null : dto.nome(), "Nome da cor", 50);
        v.verificar();
        return nome;
    }
}
