package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FotoEntity;

@Repository
public interface FotoRepository extends JpaRepository<FotoEntity, Integer> {
    
}
