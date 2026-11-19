package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.StatusOsEntity;

@Repository
public interface StatusOsRepository extends JpaRepository<StatusOsEntity, Integer> {

    Optional<StatusOsEntity> findByStatus(String status);

    List<StatusOsEntity> findAllByOrderByCodigo();

}
