package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.HistoricoStatusOsEntity;
import br.fatec.projetoextensao.entities.HistoricoStatusOsId;

@Repository
public interface HistoricoStatusOsRepository extends JpaRepository<HistoricoStatusOsEntity, HistoricoStatusOsId> {
    
}
