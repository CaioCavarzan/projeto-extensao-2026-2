package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CronogramaEntity;

@Repository
public interface CronogramaRepository extends JpaRepository<CronogramaEntity, Integer> {
    
}
