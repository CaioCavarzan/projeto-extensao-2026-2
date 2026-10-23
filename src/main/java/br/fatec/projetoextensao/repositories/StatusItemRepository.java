package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.StatusItemEntity;

@Repository
public interface StatusItemRepository extends JpaRepository<StatusItemEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-SIT-01: o Java se refere a cada status pelo nome fixo (StatusItemEnum).
    Optional<StatusItemEntity> findByStatus(String status);

    // INCLUÍDO (20/10/2026): Listagem na ordem de cadastro.
    List<StatusItemEntity> findAllByOrderByCodigo();

}
