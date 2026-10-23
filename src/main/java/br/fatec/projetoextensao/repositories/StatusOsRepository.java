package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.StatusOsEntity;

@Repository
public interface StatusOsRepository extends JpaRepository<StatusOsEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-SOS-01: o Java se refere a cada status pelo nome fixo (StatusOsEnum).
    Optional<StatusOsEntity> findByStatus(String status);

    // INCLUÍDO (20/10/2026): Listagem na ordem de cadastro.
    List<StatusOsEntity> findAllByOrderByCodigo();

}
