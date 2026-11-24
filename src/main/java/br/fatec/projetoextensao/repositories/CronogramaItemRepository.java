package br.fatec.projetoextensao.repositories;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CronogramaItemEntity;
import br.fatec.projetoextensao.entities.CronogramaItemId;
import br.fatec.projetoextensao.entities.CronogramaEntity;
import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;

@Repository
public interface CronogramaItemRepository extends JpaRepository<CronogramaItemEntity, CronogramaItemId> {

    List<CronogramaItemEntity> findByCronogramaOrderByPosicao(CronogramaEntity cronograma);

    boolean existsByItemRotaProcesso(ItemRotaProcessoEntity itemRotaProcesso);

    @Query("select coalesce(sum(ci.qtdPlanejado), 0) from CronogramaItemEntity ci where ci.itemRotaProcesso = :etapa")
    BigDecimal somarPlanejado(@Param("etapa") ItemRotaProcessoEntity etapa);

}
