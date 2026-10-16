package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.RotaProcessoEntity;

@Repository
public interface RotaProcessoRepository extends JpaRepository<RotaProcessoEntity, Integer> {
    
}
