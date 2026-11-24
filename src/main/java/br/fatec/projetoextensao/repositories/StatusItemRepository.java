package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.StatusItemEntity;

@Repository
public interface StatusItemRepository extends JpaRepository<StatusItemEntity, Integer> {

    Optional<StatusItemEntity> findByStatus(String status);

    List<StatusItemEntity> findAllByOrderByCodigo();

}
