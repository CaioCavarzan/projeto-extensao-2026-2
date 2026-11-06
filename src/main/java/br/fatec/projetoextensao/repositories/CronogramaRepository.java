package br.fatec.projetoextensao.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CronogramaEntity;

@Repository
public interface CronogramaRepository extends JpaRepository<CronogramaEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-CRO-02: um cronograma por dia (o banco não tem UK em data_planejada).
    boolean existsByDataPlanejada(LocalDate dataPlanejada);

    // INCLUÍDO (20/10/2026): RN-CRO-02: na alteração, a data pode ser a do próprio cronograma.
    boolean existsByDataPlanejadaAndCodigoNot(LocalDate dataPlanejada, int codigo);

    // INCLUÍDO (20/10/2026): Cronograma do dia.
    Optional<CronogramaEntity> findByDataPlanejada(LocalDate dataPlanejada);

    // INCLUÍDO (20/10/2026): Listagem do dia mais novo para o mais antigo.
    List<CronogramaEntity> findAllByOrderByDataPlanejadaDesc();

}
