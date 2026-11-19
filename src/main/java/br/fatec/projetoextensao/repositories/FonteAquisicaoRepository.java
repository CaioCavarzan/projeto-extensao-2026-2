package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;

@Repository
public interface FonteAquisicaoRepository extends JpaRepository<FonteAquisicaoEntity, Integer> {

    Optional<FonteAquisicaoEntity> findByDescricaoIgnoreCase(String descricao);

    boolean existsByDescricaoIgnoreCase(String descricao);

    boolean existsByDescricaoIgnoreCaseAndCodigoNot(String descricao, int codigo);

    List<FonteAquisicaoEntity> findAllByOrderByDescricao();

}
