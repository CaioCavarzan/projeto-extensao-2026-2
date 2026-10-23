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

    // INCLUÍDO (20/10/2026): RN-HIT-03: status atual do item = registro mais recente do histórico.
    Optional<HistoricoStatusItemEntity> findTopByItemOrderByDataHoraDesc(ItemEntity item);

    // INCLUÍDO (20/10/2026): Histórico completo do item, do mais antigo para o mais novo.
    List<HistoricoStatusItemEntity> findByItemOrderByDataHora(ItemEntity item);

}
