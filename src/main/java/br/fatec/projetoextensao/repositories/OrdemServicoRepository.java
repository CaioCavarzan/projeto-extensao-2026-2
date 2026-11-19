package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import br.fatec.projetoextensao.entities.OrdemServicoEntity;
import br.fatec.projetoextensao.entities.ClienteEntity;

@Repository
public interface OrdemServicoRepository extends JpaRepository<OrdemServicoEntity, Integer> {

    boolean existsByCliente(ClienteEntity cliente);

    List<OrdemServicoEntity> findAllByOrderByNumeroDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdemServicoEntity o where o.numero = :numero")
    Optional<OrdemServicoEntity> buscarParaAtualizar(@Param("numero") int numero);

}
