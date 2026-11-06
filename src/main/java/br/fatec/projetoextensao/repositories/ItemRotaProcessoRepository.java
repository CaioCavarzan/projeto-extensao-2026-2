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

    // INCLUÍDO (20/10/2026): Rota do item na ordem de execução (RN-IRP-01, RN-IRP-05, RN-IRP-06).
    List<ItemRotaProcessoEntity> findByItemOrderByOrdem(ItemEntity item);

    // INCLUÍDO (20/10/2026): RN-ROT-01: etapa usada em algum item não é excluída.
    boolean existsByRotaProcesso(RotaProcessoEntity rotaProcesso);

    // INCLUÍDO (20/10/2026): RN-FUN-02: funcionário que operou etapa não é excluído.
    boolean existsByFuncionario(FuncionarioEntity funcionario);

    // INCLUÍDO (20/10/2026): RN-IRP-01: a próxima ordem é a maior ordem do item mais 1 (0 quando o item ainda não tem etapa).
    @Query("select coalesce(max(e.ordem), 0) from ItemRotaProcessoEntity e where e.item = :item")
    int maiorOrdem(@Param("item") ItemEntity item);

    // INCLUÍDO (20/10/2026): RN-IRP-09: etapas dos itens finalizados no período (cálculo do % de retrabalho).
    List<ItemRotaProcessoEntity> findByItemIn(Collection<ItemEntity> itens);

    // INCLUÍDO (21/10/2026): fila de produção (tela Produção e montagem do cronograma): etapas ainda não concluídas.
    List<ItemRotaProcessoEntity> findByDataConclusaoIsNull();

    // INCLUÍDO (21/10/2026): dashboard: etapas concluídas no dia (DashboardService.resumo).
    long countByDataConclusaoGreaterThanEqualAndDataConclusaoLessThan(LocalDateTime inicio, LocalDateTime fim);

}
