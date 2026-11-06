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

    // INCLUÍDO (20/10/2026): RN-CLI-02: cliente com OS não é excluído.
    boolean existsByCliente(ClienteEntity cliente);

    // INCLUÍDO (20/10/2026): Listagem da OS mais nova para a mais antiga.
    List<OrdemServicoEntity> findAllByOrderByNumeroDesc();

    // INCLUÍDO (20/10/2026): RN-HOS-02 e RN-OS-14: mudança de status e recálculo do valor com a OS travada (duas mudanças ao mesmo tempo não se cruzam).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdemServicoEntity o where o.numero = :numero")
    Optional<OrdemServicoEntity> buscarParaAtualizar(@Param("numero") int numero);

}
