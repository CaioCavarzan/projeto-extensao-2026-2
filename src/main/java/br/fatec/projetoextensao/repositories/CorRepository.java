package br.fatec.projetoextensao.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CorEntity;

@Repository
public interface CorRepository extends JpaRepository<CorEntity, Integer> {

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndCodigoNot(String nome, int codigo);

    List<CorEntity> findAllByOrderByNome();

}
