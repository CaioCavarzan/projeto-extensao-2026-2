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
 * Etapas do processo (tabela RotasProcessos).
 */

@Service
@RequiredArgsConstructor
public class RotaProcessoService {

    public static final String JATEAMENTO = "Jateamento";
    public static final String BANHO = "Banho";
    public static final String PINTURA = "Pintura";

    private final RotaProcessoRepository rotaProcessoRepository;
    private final ItemRotaProcessoRepository itemRotaProcessoRepository;

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
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa não encontrada.")); 
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
