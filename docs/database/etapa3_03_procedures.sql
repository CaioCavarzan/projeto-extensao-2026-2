-- =================================================================================================
-- arquivo : docs/database/etapa3_03_procedures.sql
-- banco   : banco_fenix (sql server)
-- ordem   : 3º script da etapa 3 (depende de dbo.fn_valor_os)
-- objetos : 1. dbo.sp_ajustar_estoque_tinta
--           2. dbo.sp_cancelar_ordem_servico
--           3. dbo.sp_replanejar_cronograma
-- =================================================================================================

use banco_fenix
go

-- -------------------------------------------------------------------------------------------------
-- 1. stored procedure: dbo.sp_ajustar_estoque_tinta
-- tabela base : dbo.Tintas
-- finalidade  : realiza a movimentação atômica de estoque de uma tinta em kg (entrada quando
--               @quantidade > 0; baixa quando @quantidade < 0). utiliza bloqueio pessimista de
--               linha (updlock, rowlock) dentro de transação explícita para impedir concorrência
--               (lost update) e garante que o saldo resultante nunca fique negativo.
-- -------------------------------------------------------------------------------------------------
create or alter procedure dbo.sp_ajustar_estoque_tinta
    @tinta_codigo       int,
    @quantidade         decimal(10, 2),
    @estoque_resultante decimal(10, 2) output
as
begin
    set nocount on;
    set xact_abort on;

    if @quantidade is null or @quantidade = 0
        throw 50001, 'Informe uma quantidade diferente de zero para ajustar o estoque.', 1;

    begin try
        begin tran;

        declare @saldo_atual decimal(10, 2);

        select @saldo_atual = t.estoque_atual
        from dbo.Tintas as t with (updlock, rowlock)
        where t.codigo = @tinta_codigo;

        if @saldo_atual is null
            throw 50002, 'Tinta não encontrada.', 1;

        if (@saldo_atual + @quantidade) < 0
            throw 50003, 'Estoque insuficiente para realizar a baixa.', 1;

        set @estoque_resultante = @saldo_atual + @quantidade;

        update dbo.Tintas
        set estoque_atual = @estoque_resultante
        where codigo = @tinta_codigo;

        commit tran;
    end try
    begin catch
        if @@trancount > 0
            rollback tran;
        throw;
    end catch;
end;
go

-- -------------------------------------------------------------------------------------------------
-- 2. stored procedure: dbo.sp_cancelar_ordem_servico
-- tabelas base : dbo.OrdensDeServico, dbo.HistoricosStatusOs, dbo.StatusOs, dbo.Itens,
--                dbo.HistoricosStatusItens, dbo.StatusItens, dbo.Cronogramas,
--                dbo.CronogramasItens, dbo.ItensRotasProcessos
-- finalidade   : executa o cancelamento de uma ordem de serviço em cascata dentro de uma única
--                transação:
--                  1) valida a existência da os e impede o cancelamento caso já esteja encerrada;
--                  2) insere o status 'Cancelada' em lote para todos os itens ainda abertos da os;
--                  3) remove de cronogramas futuros as etapas não concluídas desses itens;
--                  4) insere o status 'Cancelada' no histórico da os;
--                  5) atualiza OrdensDeServico.valor_os utilizando dbo.fn_valor_os.
-- -------------------------------------------------------------------------------------------------
create or alter procedure dbo.sp_cancelar_ordem_servico
    @nr_os            int,
    @itens_cancelados int output
as
begin
    set nocount on;
    set xact_abort on;

    begin try
        begin tran;

        if not exists (
            select 1
            from dbo.OrdensDeServico with (updlock, rowlock)
            where nr_os = @nr_os
        )
            throw 50010, 'Ordem de serviço não encontrada.', 1;

        declare @status_atual_os    varchar(50),
                @ultima_data_os     datetime,
                @status_os_cancel   int,
                @status_item_cancel int,
                @agora              datetime = dateadd(millisecond, -datepart(millisecond, sysdatetime()), cast(sysdatetime() as datetime));

        select top (1)
            @status_atual_os = so.status,
            @ultima_data_os  = hso.data_hora
        from dbo.HistoricosStatusOs as hso
        inner join dbo.StatusOs     as so on so.codigo = hso.status_codigo
        where hso.os_codigo = @nr_os
        order by hso.data_hora desc;

        if @status_atual_os like 'Conclu_da'
            throw 50011, 'OS concluída não pode ser cancelada.', 1;

        if @status_atual_os = 'Cancelada'
            throw 50012, 'A OS já está cancelada.', 1;

        select @status_os_cancel   = codigo from dbo.StatusOs    where status = 'Cancelada';
        select @status_item_cancel = codigo from dbo.StatusItens where status = 'Cancelada';

        -- cancela em lote todos os itens da os que ainda não estão encerrados
        insert into dbo.HistoricosStatusItens (item_codigo, status_item_codigo, data_hora)
        select
            i.codigo,
            @status_item_cancel,
            case
                when st.data_hora is not null and @agora <= st.data_hora
                    then dateadd(second, 1, st.data_hora)
                else @agora
            end
        from dbo.Itens as i with (updlock, rowlock)
        cross apply (
            select top (1)
                si.status,
                hsi.data_hora
            from dbo.HistoricosStatusItens as hsi
            inner join dbo.StatusItens     as si on si.codigo = hsi.status_item_codigo
            where hsi.item_codigo = i.codigo
            order by hsi.data_hora desc
        ) as st
        where i.os_codigo = @nr_os
          and st.status not in ('Executada', 'Cancelada');

        set @itens_cancelados = @@rowcount;

        -- remove etapas não concluídas dos cronogramas futuros
        delete ci
        from dbo.CronogramasItens          as ci
        inner join dbo.Cronogramas         as c   on c.codigo   = ci.cronograma_codigo
        inner join dbo.ItensRotasProcessos as irp on irp.codigo = ci.item_rota_processo_codigo
        inner join dbo.Itens               as i   on i.codigo   = irp.item_codigo
        where i.os_codigo = @nr_os
          and irp.data_conclusao is null
          and c.data_planejada >= cast(sysdatetime() as date);

        -- registra o status 'Cancelada' na os
        insert into dbo.HistoricosStatusOs (os_codigo, status_codigo, data_hora)
        values (
            @nr_os,
            @status_os_cancel,
            case
                when @ultima_data_os is not null and @agora <= @ultima_data_os
                    then dateadd(second, 1, @ultima_data_os)
                else @agora
            end
        );

        -- sincroniza o valor_os da os
        update dbo.OrdensDeServico
        set valor_os = dbo.fn_valor_os(@nr_os)
        where nr_os = @nr_os;

        commit tran;
    end try
    begin catch
        if @@trancount > 0
            rollback tran;
        throw;
    end catch;
end;
go

-- -------------------------------------------------------------------------------------------------
-- 3. stored procedure: dbo.sp_replanejar_cronograma
-- tabelas base : dbo.Cronogramas, dbo.CronogramasItens, dbo.ItensRotasProcessos, dbo.Itens,
--                dbo.Usuarios, dbo.HistoricosStatusItens, dbo.HistoricosStatusOs
-- finalidade   : rotina de fechamento diário de produção: transfere todas as etapas planejadas em
--                @data_origem que ainda não foram concluídas (de itens ativos e os abertas) para o
--                cronograma de @data_destino. caso não exista cronograma na data de destino, cria
--                o cabeçalho automaticamente e numera a coluna posicao de forma sequencial.
-- -------------------------------------------------------------------------------------------------
create or alter procedure dbo.sp_replanejar_cronograma
    @data_origem    date,
    @data_destino   date,
    @usuario_codigo int,
    @etapas_movidas int output
as
begin
    set nocount on;
    set xact_abort on;

    if @data_origem is null or @data_destino is null or @data_destino <= @data_origem
        throw 50020, 'A data de destino deve ser posterior à data de origem.', 1;

    if not exists (
        select 1
        from dbo.Usuarios
        where funcionario_codigo = @usuario_codigo
          and ativo = 1
    )
        throw 50021, 'Usuário inválido ou inativo.', 1;

    begin try
        begin tran;

        declare @cronograma_origem  int,
                @cronograma_destino int,
                @proxima_posicao    int;

        select top (1) @cronograma_origem = codigo
        from dbo.Cronogramas
        where data_planejada = @data_origem
        order by codigo asc;

        if @cronograma_origem is null
        begin
            set @etapas_movidas = 0;
            commit tran;
            return;
        end;

        select top (1) @cronograma_destino = codigo
        from dbo.Cronogramas with (updlock, holdlock)
        where data_planejada = @data_destino
        order by codigo asc;

        if @cronograma_destino is null
        begin
            insert into dbo.Cronogramas (usuario_codigo, data_planejada, observacao)
            values (
                @usuario_codigo,
                @data_destino,
                concat('Replanejado a partir de ', convert(varchar(10), @data_origem, 23))
            );
            set @cronograma_destino = scope_identity();
        end;

        select @proxima_posicao = isnull(max(posicao) + 1, 0)
        from dbo.CronogramasItens with (updlock, holdlock)
        where cronograma_codigo = @cronograma_destino;

        ;with cte_etapas_pendentes as (
            select
                ci.item_rota_processo_codigo,
                ci.qtd_planejado,
                ci.pontuacao,
                row_number() over (order by ci.posicao asc) - 1 as deslocamento
            from dbo.CronogramasItens          as ci
            inner join dbo.ItensRotasProcessos as irp on irp.codigo = ci.item_rota_processo_codigo
            inner join dbo.Itens               as i   on i.codigo   = irp.item_codigo
            cross apply (
                select top (1) si.status
                from dbo.HistoricosStatusItens as hsi
                inner join dbo.StatusItens     as si on si.codigo = hsi.status_item_codigo
                where hsi.item_codigo = i.codigo
                order by hsi.data_hora desc
            ) as st_item
            cross apply (
                select top (1) so.status
                from dbo.HistoricosStatusOs as hso
                inner join dbo.StatusOs     as so on so.codigo = hso.status_codigo
                where hso.os_codigo = i.os_codigo
                order by hso.data_hora desc
            ) as st_os
            where ci.cronograma_codigo = @cronograma_origem
              and irp.data_conclusao is null
              and st_item.status not in ('Executada', 'Cancelada')
              and st_os.status   not like 'Conclu_da'
              and st_os.status   <> 'Cancelada'
              and not exists (
                  select 1
                  from dbo.CronogramasItens as destino
                  where destino.cronograma_codigo         = @cronograma_destino
                    and destino.item_rota_processo_codigo = ci.item_rota_processo_codigo
              )
        )
        insert into dbo.CronogramasItens
        (
            cronograma_codigo,
            item_rota_processo_codigo,
            qtd_planejado,
            posicao,
            pontuacao
        )
        select
            @cronograma_destino,
            item_rota_processo_codigo,
            qtd_planejado,
            @proxima_posicao + deslocamento,
            pontuacao
        from cte_etapas_pendentes;

        set @etapas_movidas = @@rowcount;

        commit tran;
    end try
    begin catch
        if @@trancount > 0
            rollback tran;
        throw;
    end catch;
end;
go
