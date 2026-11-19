package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.RotaProcessoEntity;

@Repository
public interface RotaProcessoRepository extends JpaRepository<RotaProcessoEntity, Integer> {

    Optional<RotaProcessoEntity> findByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndCodigoNot(String nome, int codigo);

    List<RotaProcessoEntity> findAllByOrderByCodigo();

}
