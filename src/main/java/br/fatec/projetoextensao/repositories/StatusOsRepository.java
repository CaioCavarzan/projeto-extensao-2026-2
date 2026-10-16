package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.StatusOsEntity;

@Repository
public interface StatusOsRepository extends JpaRepository<StatusOsEntity, Integer> {
    
}
