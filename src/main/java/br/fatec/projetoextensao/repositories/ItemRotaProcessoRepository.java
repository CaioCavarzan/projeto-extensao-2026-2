package br.fatec.projetoextensao.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.ItemRotaProcessoEntity;
import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.RotaProcessoEntity;
import br.fatec.projetoextensao.entities.FuncionarioEntity;

@Repository
public interface ItemRotaProcessoRepository extends JpaRepository<ItemRotaProcessoEntity, Integer> {

    List<ItemRotaProcessoEntity> findByItemOrderByOrdem(ItemEntity item);

    boolean existsByRotaProcesso(RotaProcessoEntity rotaProcesso);

    boolean existsByFuncionario(FuncionarioEntity funcionario);

    @Query("select coalesce(max(e.ordem), 0) from ItemRotaProcessoEntity e where e.item = :item")
    int maiorOrdem(@Param("item") ItemEntity item);

    List<ItemRotaProcessoEntity> findByItemIn(Collection<ItemEntity> itens);

    List<ItemRotaProcessoEntity> findByDataConclusaoIsNull();

    long countByDataConclusaoGreaterThanEqualAndDataConclusaoLessThan(LocalDateTime inicio, LocalDateTime fim);

}
