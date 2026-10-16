package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.StatusItemEntity;

@Repository
public interface StatusItemRepository extends JpaRepository<StatusItemEntity, Integer> {
    
}
