package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.HistoricoStatusItemEntity;
import br.fatec.projetoextensao.entities.HistoricoStatusItemId;
import br.fatec.projetoextensao.entities.ItemEntity;

@Repository
public interface HistoricoStatusItemRepository extends JpaRepository<HistoricoStatusItemEntity, HistoricoStatusItemId> {

    Optional<HistoricoStatusItemEntity> findTopByItemOrderByDataHoraDesc(ItemEntity item);

    List<HistoricoStatusItemEntity> findByItemOrderByDataHora(ItemEntity item);

}
