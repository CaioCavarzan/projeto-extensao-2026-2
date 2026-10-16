package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.HistoricoStatusItemEntity;
import br.fatec.projetoextensao.entities.HistoricoStatusItemId;

@Repository
public interface HistoricoStatusItemRepository extends JpaRepository<HistoricoStatusItemEntity, HistoricoStatusItemId> {
    
}
