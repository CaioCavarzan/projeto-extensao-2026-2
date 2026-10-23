package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;

@Repository
public interface FonteAquisicaoRepository extends JpaRepository<FonteAquisicaoEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-CLI-06 e RN-FON-01: localizar a fonte 'Não informada'.
    Optional<FonteAquisicaoEntity> findByDescricaoIgnoreCase(String descricao);

    // INCLUÍDO (20/10/2026): RN-FON-04: descrição única (UK uq_fontes_aquisicao_descricao).
    boolean existsByDescricaoIgnoreCase(String descricao);

    // INCLUÍDO (20/10/2026): RN-FON-04: na renomeação, a descrição pode ser a da própria fonte.
    boolean existsByDescricaoIgnoreCaseAndCodigoNot(String descricao, int codigo);

    // INCLUÍDO (20/10/2026): Listagem em ordem alfabética.
    List<FonteAquisicaoEntity> findAllByOrderByDescricao();

}
