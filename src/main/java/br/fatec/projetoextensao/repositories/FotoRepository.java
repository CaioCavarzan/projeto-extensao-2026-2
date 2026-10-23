package br.fatec.projetoextensao.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FotoEntity;
import br.fatec.projetoextensao.entities.ItemEntity;

@Repository
public interface FotoRepository extends JpaRepository<FotoEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-FOT-02: no máximo 10 fotos por item.
    long countByItem(ItemEntity item);

    // INCLUÍDO (20/10/2026): Fotos do item na ordem de envio.
    List<FotoEntity> findByItemOrderByCodigo(ItemEntity item);

}
