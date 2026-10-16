package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CronogramaItemEntity;
import br.fatec.projetoextensao.entities.CronogramaItemId;

@Repository
public interface CronogramaItemRepository extends JpaRepository<CronogramaItemEntity, CronogramaItemId> {
    
}
