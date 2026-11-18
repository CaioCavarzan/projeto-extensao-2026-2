-- =================================================================================================
-- arquivo : docs/database/etapa3_04_triggers.sql
-- banco   : banco_fenix (sql server)
-- ordem   : 4º script da etapa 3 (depende de dbo.fn_valor_os)
-- objetos : 1. dbo.trg_itens_sincronizar_valor_os
--           2. dbo.trg_historicos_status_os_proteger
--           3. dbo.trg_historicos_status_itens_sincronizar_item_e_os
-- =================================================================================================

use banco_fenix
go

-- -------------------------------------------------------------------------------------------------
-- 1. trigger: dbo.trg_itens_sincronizar_valor_os
-- tabela alvo : dbo.Itens (after insert, update, delete)
-- finalidade  : utilizando as tabelas lógicas inserted e deleted:
--                 1) valida a consistência entre a cor do item (Itens.cor_codigo) e a cor da tinta
--                    selecionada (Tintas.cor_codigo), abortando a transação em caso de divergência;
--                 2) recalcula e atualiza automaticamente OrdensDeServico.valor_os para todas as
--                    os afetadas pela operação.
-- -------------------------------------------------------------------------------------------------
create or alter trigger dbo.trg_itens_sincronizar_valor_os
on dbo.Itens
after insert, update, delete
as
begin
    set nocount on;

    -- 1. validação de consistência: cor do item deve coincidir com a cor da tinta selecionada
    if exists (
        select 1
        from inserted         as ins
        inner join dbo.Tintas as t on t.codigo = ins.tinta_codigo
        where ins.tinta_codigo is not null
          and ins.cor_codigo <> t.cor_codigo
    )
    begin
        if @@trancount > 0
            rollback tran;
        throw 50110, 'Inconsistência no item: a cor informada diverge da cor cadastrada na tinta.', 1;
    end;

    -- 2. atualização automática do subtotal valor_os na tabela pai (OrdensDeServico)
    ;with cte_os_afetadas as (
        select os_codigo from inserted
        union
        select os_codigo from deleted
    )
    update os
    set os.valor_os = dbo.fn_valor_os(os.nr_os)
    from dbo.OrdensDeServico   as os
    inner join cte_os_afetadas as a on a.os_codigo = os.nr_os;
end;
go

-- -------------------------------------------------------------------------------------------------
-- 2. trigger: dbo.trg_historicos_status_os_proteger
-- tabela alvo : dbo.HistoricosStatusOs (after insert, update, delete)
-- finalidade  : garante a integridade do histórico e do ciclo de vida das ordens de serviço:
--                 1) bloqueia qualquer tentativa de update ou delete (tabela append-only);
--                 2) bloqueia a inserção de novos status em os cujo status anterior já era
--                    terminal ('Concluída' ou 'Cancelada').
-- -------------------------------------------------------------------------------------------------
create or alter trigger dbo.trg_historicos_status_os_proteger
on dbo.HistoricosStatusOs
after insert, update, delete
as
begin
    set nocount on;

    -- 1. bloqueia alterações ou exclusões no histórico de status da os
    if exists (select 1 from deleted)
    begin
        if @@trancount > 0
            rollback tran;
        throw 50101, 'O histórico de status da OS é imutável: não são permitidos UPDATE ou DELETE.', 1;
    end;

    -- 2. bloqueia reabertura ou transição a partir de os já encerrada ('Concluída' ou 'Cancelada')
    if exists (
        select 1
        from inserted as ins
        cross apply (
            select top (1) so_ant.status
            from dbo.HistoricosStatusOs as hso_ant
            inner join dbo.StatusOs     as so_ant on so_ant.codigo = hso_ant.status_codigo
            where hso_ant.os_codigo = ins.os_codigo
              and not (
                  hso_ant.status_codigo = ins.status_codigo
                  and hso_ant.data_hora = ins.data_hora
              )
              and hso_ant.data_hora <= ins.data_hora
            order by hso_ant.data_hora desc
        ) as status_anterior
        where status_anterior.status like 'Conclu_da'
           or status_anterior.status = 'Cancelada'
    )
    begin
        if @@trancount > 0
            rollback tran;
        throw 50102, 'OS encerrada (Concluída ou Cancelada) não aceita novas mudanças de status.', 1;
    end;
end;
go

-- -------------------------------------------------------------------------------------------------
-- 3. trigger: dbo.trg_historicos_status_itens_sincronizar_item_e_os
-- tabela alvo : dbo.HistoricosStatusItens (after insert, update, delete)
-- finalidade  : mantém o item e a os sincronizados a cada transição de status do item:
--                 1) bloqueia qualquer tentativa de update ou delete no histórico do item;
--                 2) preenche automaticamente Itens.data_finalizacao quando o item recebe o status
--                    'Executada';
--                 3) recalcula automaticamente OrdensDeServico.valor_os quando o status de um item
--                    é alterado (ex.: item cancelado deixa de compor o valor da os).
-- -------------------------------------------------------------------------------------------------
create or alter trigger dbo.trg_historicos_status_itens_sincronizar_item_e_os
on dbo.HistoricosStatusItens
after insert, update, delete
as
begin
    set nocount on;

    -- 1. bloqueia alterações ou exclusões no histórico de status do item
    if exists (select 1 from deleted)
    begin
        if @@trancount > 0
            rollback tran;
        throw 50120, 'O histórico de status do item é imutável: não são permitidos UPDATE ou DELETE.', 1;
    end;

    -- 2. preenche data_finalizacao automaticamente quando o item passa para 'Executada'
    update i
    set i.data_finalizacao = case
                                 when ins.data_hora < i.data_entrada then i.data_entrada
                                 else ins.data_hora
                             end
    from dbo.Itens             as i
    inner join inserted        as ins on ins.item_codigo = i.codigo
    inner join dbo.StatusItens as si  on si.codigo       = ins.status_item_codigo
    where si.status = 'Executada'
      and i.data_finalizacao is null;

    -- 3. recalcula o valor_os da os pai para refletir cancelamentos de itens
    ;with cte_os_afetadas as (
        select distinct i.os_codigo
        from inserted        as ins
        inner join dbo.Itens as i on i.codigo = ins.item_codigo
    )
    update os
    set os.valor_os = dbo.fn_valor_os(os.nr_os)
    from dbo.OrdensDeServico   as os
    inner join cte_os_afetadas as a on a.os_codigo = os.nr_os;
end;
go
