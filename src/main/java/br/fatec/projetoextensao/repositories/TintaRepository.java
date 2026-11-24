package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import br.fatec.projetoextensao.entities.TintaEntity;
import br.fatec.projetoextensao.entities.CorEntity;
import br.fatec.projetoextensao.entities.FornecedorEntity;

@Repository
public interface TintaRepository extends JpaRepository<TintaEntity, Integer> {

    boolean existsByCor(CorEntity cor);

    boolean existsByFornecedor(FornecedorEntity fornecedor);

    boolean existsByFornecedorAndReferenciaFornecedorIgnoreCase(FornecedorEntity fornecedor, String referencia);

    boolean existsByFornecedorAndReferenciaFornecedorIgnoreCaseAndCodigoNot(FornecedorEntity fornecedor, String referencia, int codigo);

    List<TintaEntity> findAllByOrderByDescricao();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TintaEntity t where t.codigo = :codigo")
    Optional<TintaEntity> buscarParaAtualizar(@Param("codigo") int codigo);

}
