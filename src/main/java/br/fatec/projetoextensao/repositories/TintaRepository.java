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

    // INCLUÍDO (20/10/2026): RN-COR-01: cor usada em tinta não é excluída.
    boolean existsByCor(CorEntity cor);

    // INCLUÍDO (20/10/2026): RN-FOR-02: fornecedor com tintas não é excluído (é inativado).
    boolean existsByFornecedor(FornecedorEntity fornecedor);

    // INCLUÍDO (20/10/2026): RN-TIN-12: a mesma referência não se repete para o mesmo fornecedor.
    boolean existsByFornecedorAndReferenciaFornecedorIgnoreCase(FornecedorEntity fornecedor, String referencia);

    // INCLUÍDO (20/10/2026): RN-TIN-12: na alteração, a referência pode ser a da própria tinta.
    boolean existsByFornecedorAndReferenciaFornecedorIgnoreCaseAndCodigoNot(FornecedorEntity fornecedor, String referencia, int codigo);

    // INCLUÍDO (20/10/2026): Listagem em ordem alfabética.
    List<TintaEntity> findAllByOrderByDescricao();

    // INCLUÍDO (20/10/2026): RN-TIN-14: ajuste de estoque com a linha travada (dois ajustes ao mesmo tempo não se perdem).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TintaEntity t where t.codigo = :codigo")
    Optional<TintaEntity> buscarParaAtualizar(@Param("codigo") int codigo);

}
