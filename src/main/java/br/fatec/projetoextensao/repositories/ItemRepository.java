package br.fatec.projetoextensao.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import br.fatec.projetoextensao.entities.ItemEntity;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.CorEntity;
import br.fatec.projetoextensao.entities.TintaEntity;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, Integer> {

    List<ItemEntity> findByOsOrderByCodigo(OrdemServicoEntity os);

    List<ItemEntity> findAllByOrderByCodigoDesc();

    boolean existsByCor(CorEntity cor);

    boolean existsByTinta(TintaEntity tinta);

    List<ItemEntity> findByPrazoEntregaBeforeOrderByPrazoEntrega(LocalDate data);

    List<ItemEntity> findByDataFinalizacaoGreaterThanEqualAndDataFinalizacaoLessThan(LocalDateTime inicio, LocalDateTime fim);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ItemEntity i where i.codigo = :codigo")
    Optional<ItemEntity> buscarParaAtualizar(@Param("codigo") int codigo);

}
