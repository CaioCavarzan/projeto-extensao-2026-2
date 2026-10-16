package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CorEntity;

@Repository
public interface CorRepository extends JpaRepository<CorEntity, Integer> {
    
}
