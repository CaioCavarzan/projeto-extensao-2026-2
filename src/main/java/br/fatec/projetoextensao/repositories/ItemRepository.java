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

    // INCLUÍDO (20/10/2026): Itens da OS (RN-OS-04, RN-OS-05, RN-OS-14).
    List<ItemEntity> findByOsOrderByCodigo(OrdemServicoEntity os);

    // INCLUÍDO (21/10/2026): tela Peças: todos os itens, do mais recente para o mais antigo (ItemService.listar).
    List<ItemEntity> findAllByOrderByCodigoDesc();

    // INCLUÍDO (20/10/2026): RN-COR-01: cor usada em item não é excluída.
    boolean existsByCor(CorEntity cor);

    // INCLUÍDO (20/10/2026): RN-TIN-02: tinta usada em item não é excluída.
    boolean existsByTinta(TintaEntity tinta);

    // INCLUÍDO (20/10/2026): RN-ITE-22: itens com prazo de entrega antes da data (o Service filtra os que não estão encerrados).
    List<ItemEntity> findByPrazoEntregaBeforeOrderByPrazoEntrega(LocalDate data);

    // INCLUÍDO (20/10/2026): RN-IRP-09: itens finalizados no período (base do % de retrabalho).
    List<ItemEntity> findByDataFinalizacaoGreaterThanEqualAndDataFinalizacaoLessThan(LocalDateTime inicio, LocalDateTime fim);

    // INCLUÍDO (20/10/2026): RN-HIT-02, RN-IRP-01 e RN-FOT-02: operações no item com a linha travada (status, próxima ordem e limite de fotos).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ItemEntity i where i.codigo = :codigo")
    Optional<ItemEntity> buscarParaAtualizar(@Param("codigo") int codigo);

}
