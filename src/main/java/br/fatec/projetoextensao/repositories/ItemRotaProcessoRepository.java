package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;

@Repository
public interface ItemRotaProcessoRepository extends JpaRepository<ItemRotaProcessoEntity, Integer> {
    
}
