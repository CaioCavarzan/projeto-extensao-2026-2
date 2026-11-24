package br.fatec.projetoextensao.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FotoEntity;
import br.fatec.projetoextensao.entities.ItemEntity;

@Repository
public interface FotoRepository extends JpaRepository<FotoEntity, Integer> {

    long countByItem(ItemEntity item);

    List<FotoEntity> findByItemOrderByCodigo(ItemEntity item);

}
