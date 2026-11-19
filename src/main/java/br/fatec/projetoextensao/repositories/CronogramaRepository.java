package br.fatec.projetoextensao.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CronogramaEntity;

@Repository
public interface CronogramaRepository extends JpaRepository<CronogramaEntity, Integer> {

    boolean existsByDataPlanejada(LocalDate dataPlanejada);

    boolean existsByDataPlanejadaAndCodigoNot(LocalDate dataPlanejada, int codigo);

    Optional<CronogramaEntity> findByDataPlanejada(LocalDate dataPlanejada);

    List<CronogramaEntity> findAllByOrderByDataPlanejadaDesc();

}
