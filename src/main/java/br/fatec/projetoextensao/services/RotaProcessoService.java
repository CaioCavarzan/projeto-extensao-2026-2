package br.fatec.projetoextensao.services;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.entities.RotaProcessoEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.RotaProcessoRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): etapas do processo (tabela RotasProcessos).
 * Regras: RN-ROT-01 (etapas cadastradas; etapa em uso não é excluída), RN-ROT-03 e RN-ROT-04 (nome único, até
 * 25 caracteres), RN-SIS-03 (alterar listas de apoio: só ADMIN).
 *
 * RN-ROT-02 (rotas padrão sugeridas na abertura do item) fica no front, como diz a planilha.
 */
@Service
@RequiredArgsConstructor
public class RotaProcessoService {

    /** RN-ROT-01: etapas cadastradas na carga inicial. */
    public static final String JATEAMENTO = "Jateamento";
    public static final String BANHO = "Banho";
    /** RN-ITE-05 usa esta etapa pelo nome: item com Pintura na rota tem cor pedida. */
    public static final String PINTURA = "Pintura";

    private final RotaProcessoRepository rotaProcessoRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;

    /** true quando a etapa é a Pintura (RN-ITE-05). */
    public static boolean ehPintura(RotaProcessoEntity rota) {
        return rota != null && PINTURA.equalsIgnoreCase(rota.getNome());
    }

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public List<ItemListaResponse> listar() {
        return rotaProcessoRepository.findAllByOrderByCodigo().stream()
                .map(r -> new ItemListaResponse(r.getCodigo(), r.getNome()))
                .toList();
    }

    /** RN-ROT-04: nome obrigatório, até 25 caracteres e único. RN-SIS-03: só ADMIN altera a lista. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public ItemListaResponse cadastrar(NomeRequest dto) {
        String nome = validarNome(dto);
        if (rotaProcessoRepository.existsByNomeIgnoreCase(nome)) {
            throw RegraNegocioException.conflito("Esta etapa já existe.");
        }
        RotaProcessoEntity rota = new RotaProcessoEntity();
        rota.setNome(nome);
        rota = rotaProcessoRepository.save(rota);
        return new ItemListaResponse(rota.getCodigo(), rota.getNome());
    }

    /**
     * RN-ROT-04 e RN-SIS-03. A Pintura não é renomeada: as regras do item (RN-ITE-05) a reconhecem pelo nome.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public ItemListaResponse renomear(int codigo, NomeRequest dto) {
        RotaProcessoEntity rota = buscarEntidade(codigo);
        protegerPintura(rota);
        String nome = validarNome(dto);
        if (rotaProcessoRepository.existsByNomeIgnoreCaseAndCodigoNot(nome, codigo)) {
            throw RegraNegocioException.conflito("Esta etapa já existe.");
        }
        rota.setNome(nome);
        return new ItemListaResponse(rota.getCodigo(), rotaProcessoRepository.save(rota).getNome());
    }

    /** RN-ROT-01: etapa usada em algum item não é excluída. RN-SIS-03: só ADMIN. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public void excluir(int codigo) {
        RotaProcessoEntity rota = buscarEntidade(codigo);
        protegerPintura(rota);
        if (itemRotaProcessoRepository.existsByRotaProcesso(rota)) {
            throw RegraNegocioException.conflito("Etapa em uso não pode ser excluída.");
        }
        rotaProcessoRepository.delete(rota);
    }

    RotaProcessoEntity buscarEntidade(int codigo) {
        return rotaProcessoRepository.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa não encontrada.")); // RN-IRP-11
    }

    private void protegerPintura(RotaProcessoEntity rota) {
        if (ehPintura(rota)) {
            throw new RegraNegocioException("Esta etapa é usada pelo sistema e não pode ser alterada.");
        }
    }

    private String validarNome(NomeRequest dto) {
        Validacao v = new Validacao();
        String nome = v.obrigatorio(dto == null ? null : dto.nome(), "Nome da etapa", 25);
        v.verificar();
        return nome;
    }
}
