package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.TintaEntity;

@Repository
public interface TintaRepository extends JpaRepository<TintaEntity, Integer> {
    
}
