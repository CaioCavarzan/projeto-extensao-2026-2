-- =================================================================================================
-- arquivo : docs/database/etapa3_01_functions.sql
-- banco   : banco_fenix (sql server)
-- ordem   : 1º script da etapa 3 (pré-requisito para views, procedures e triggers)
-- objetos : 1. dbo.fn_valor_os              (scalar function)
--           2. dbo.fn_itens_atrasados       (inline table-valued function)
--           3. dbo.fn_prioridade_clientes   (multi-statement table-valued function com variável table)
--           4. dbo.fn_auditoria_timeline_os (inline table-valued function de rastreabilidade)
-- =================================================================================================

use banco_fenix
go

-- -------------------------------------------------------------------------------------------------
-- 1. função escalar: dbo.fn_valor_os
-- tabelas base : dbo.Itens, dbo.HistoricosStatusItens, dbo.StatusItens
-- finalidade   : calcula e retorna o valor líquido de uma ordem de serviço (decimal(18,2)), somando
--                a coluna valor_total_item apenas dos itens cujo status atual (último registro em
--                HistoricosStatusItens) seja diferente de 'Cancelada'. itens com valor null contam
--                como 0.00.
-- -------------------------------------------------------------------------------------------------
create or alter function dbo.fn_valor_os (@nr_os int)
returns decimal(18, 2)
as
begin
    declare @valor_calculado decimal(18, 2);

    select @valor_calculado = sum(i.valor_total_item)
    from dbo.Itens as i
    outer apply (
        select top (1) si.status
        from dbo.HistoricosStatusItens as hsi
        inner join dbo.StatusItens     as si on si.codigo = hsi.status_item_codigo
        where hsi.item_codigo = i.codigo
        order by hsi.data_hora desc
    ) as status_atual
    where i.os_codigo = @nr_os
      and isnull(status_atual.status, '') <> 'Cancelada';

    return isnull(@valor_calculado, 0.00);
end;
go

-- -------------------------------------------------------------------------------------------------
-- 2. função inline table-valued: dbo.fn_itens_atrasados
-- tabelas base : dbo.Itens, dbo.OrdensDeServico, dbo.Pessoas, dbo.HistoricosStatusItens,
--                dbo.StatusItens, dbo.ItensRotasProcessos, dbo.RotasProcessos
-- finalidade   : retorna uma tabela filtrada contendo todas as peças com prazo_entrega vencido em
--                relação a @data_referencia e que ainda não foram encerradas ('Executada' ou
--                'Cancelada'), exibindo os dias de atraso e a próxima etapa pendente com seu
--                respectivo operador.
-- -------------------------------------------------------------------------------------------------
create or alter function dbo.fn_itens_atrasados (@data_referencia date)
returns table
as
return
(
    select
        i.codigo                                         as item_codigo,
        i.os_codigo                                      as nr_os,
        p_cli.nome                                       as cliente_nome,
        i.descricao                                      as item_descricao,
        i.qtd_total,
        i.unidade,
        i.prazo_entrega,
        datediff(day, i.prazo_entrega, @data_referencia) as dias_atraso,
        status_item.status                               as status_atual,
        prox_etapa.processo                              as etapa_pendente,
        prox_etapa.operador                              as operador_etapa
    from dbo.Itens                 as i
    inner join dbo.OrdensDeServico as os    on os.nr_os     = i.os_codigo
    inner join dbo.Pessoas         as p_cli on p_cli.codigo = os.cliente_codigo
    cross apply (
        select top (1) si.status
        from dbo.HistoricosStatusItens as hsi
        inner join dbo.StatusItens     as si on si.codigo = hsi.status_item_codigo
        where hsi.item_codigo = i.codigo
        order by hsi.data_hora desc
    ) as status_item
    outer apply (
        select top (1)
            rp.nome    as processo,
            p_fun.nome as operador
        from dbo.ItensRotasProcessos  as irp
        inner join dbo.RotasProcessos as rp    on rp.codigo    = irp.rota_processo_codigo
        left join dbo.Pessoas         as p_fun on p_fun.codigo = irp.funcionario_codigo
        where irp.item_codigo = i.codigo
          and irp.data_conclusao is null
        order by irp.ordem asc
    ) as prox_etapa
    where i.prazo_entrega < @data_referencia
      and status_item.status not in ('Executada', 'Cancelada')
);
go

-- -------------------------------------------------------------------------------------------------
-- 3. função multi-statement table-valued (com variável tabela): dbo.fn_prioridade_clientes
-- tabelas base : dbo.Clientes, dbo.Pessoas, dbo.OrdensDeServico, dbo.HistoricosStatusOs, dbo.StatusOs
-- finalidade   : calcula o score comercial de prioridade de todos os clientes dentro da faixa
--                [@prioridade_minima, @prioridade_maxima] (ex.: 0 a 100).
--                1ª etapa: utiliza a variável @faturamento table para consolidar o faturamento total
--                de cada cliente (soma de valor_os das os não canceladas).
--                2ª etapa: obtém o maior faturamento da base e normaliza proporcionalmente a
--                pontuação na tabela de retorno @prioridades.
-- -------------------------------------------------------------------------------------------------
create or alter function dbo.fn_prioridade_clientes
(
    @prioridade_minima int,
    @prioridade_maxima int
)
returns @prioridades table
(
    cliente_codigo int            not null primary key,
    cliente_nome   varchar(200)   not null,
    faturamento    decimal(18, 2) not null,
    prioridade     int            not null
)
as
begin
    declare @faturamento table
    (
        cliente_codigo int            not null primary key,
        total_faturado decimal(18, 2) not null
    );

    declare @maior_faturamento decimal(18, 2),
            @amplitude         int = (@prioridade_maxima - @prioridade_minima);

    -- 1ª passada: consolida o faturamento válido por cliente na variável tabela interna
    insert into @faturamento (cliente_codigo, total_faturado)
    select
        c.pessoa_codigo,
        isnull(sum(os_validas.valor_os), 0.00)
    from dbo.Clientes as c
    left join (
        select
            os.cliente_codigo,
            os.valor_os
        from dbo.OrdensDeServico as os
        outer apply (
            select top (1) so.status
            from dbo.HistoricosStatusOs as hso
            inner join dbo.StatusOs     as so on so.codigo = hso.status_codigo
            where hso.os_codigo = os.nr_os
            order by hso.data_hora desc
        ) as status_os
        where isnull(status_os.status, '') <> 'Cancelada'
    ) as os_validas on os_validas.cliente_codigo = c.pessoa_codigo
    group by c.pessoa_codigo;

    select @maior_faturamento = max(total_faturado)
    from @faturamento;

    -- 2ª passada: calcula a prioridade proporcional ao maior faturamento e popula a tabela de saída
    insert into @prioridades (cliente_codigo, cliente_nome, faturamento, prioridade)
    select
        f.cliente_codigo,
        p.nome,
        f.total_faturado,
        case
            when @maior_faturamento > 0 and f.total_faturado > 0
                then @prioridade_minima + cast(round((f.total_faturado * @amplitude) / @maior_faturamento, 0) as int)
            else @prioridade_minima
        end
    from @faturamento      as f
    inner join dbo.Pessoas as p on p.codigo = f.cliente_codigo;

    return;
end;
go

-- -------------------------------------------------------------------------------------------------
-- 4. função inline table-valued: dbo.fn_auditoria_timeline_os
-- tabelas base : dbo.OrdensDeServico, dbo.Usuarios, dbo.HistoricosStatusOs, dbo.StatusOs,
--                dbo.Itens, dbo.HistoricosStatusItens, dbo.StatusItens,
--                dbo.ItensRotasProcessos, dbo.RotasProcessos, dbo.Pessoas
-- finalidade   : reconstrói a linha do tempo completa de auditoria (rastreabilidade ponta a ponta)
--                de uma ordem de serviço (@nr_os), unificando em ordem cronológica:
--                  1) transições de status da própria os;
--                  2) transições de status de cada item/peça vinculada à os;
--                  3) início e conclusão de cada etapa da rota produtiva no chão de fábrica.
-- -------------------------------------------------------------------------------------------------
create or alter function dbo.fn_auditoria_timeline_os (@nr_os int)
returns table
as
return
(
    select
        row_number() over (order by eventos.data_hora asc, eventos.ordem_tipo asc, isnull(eventos.item_codigo, 0) asc) as sequencia,
        eventos.nr_os,
        eventos.data_hora,
        eventos.categoria,
        eventos.item_codigo,
        eventos.referencia,
        eventos.evento,
        eventos.responsavel
    from (
        -- 1. eventos de mudança de status da ordem de serviço
        select
            os.nr_os                                  as nr_os,
            hso.data_hora                             as data_hora,
            1                                         as ordem_tipo,
            'Ordem de Serviço'                        as categoria,
            cast(null as int)                         as item_codigo,
            concat('OS #', os.nr_os)                  as referencia,
            concat('Status da OS: ', so.status)       as evento,
            u.login                                   as responsavel
        from dbo.OrdensDeServico       as os
        inner join dbo.Usuarios        as u   on u.funcionario_codigo = os.usuario_codigo
        inner join dbo.HistoricosStatusOs as hso on hso.os_codigo     = os.nr_os
        inner join dbo.StatusOs        as so  on so.codigo            = hso.status_codigo
        where os.nr_os = @nr_os

        union all

        -- 2. eventos de mudança de status dos itens (peças) da os
        select
            i.os_codigo                                                as nr_os,
            hsi.data_hora                                              as data_hora,
            2                                                          as ordem_tipo,
            'Peça (Item)'                                              as categoria,
            i.codigo                                                   as item_codigo,
            concat('Item #', i.codigo, ' (', i.descricao, ')')         as referencia,
            concat('Status do Item: ', si.status)                      as evento,
            u.login                                                    as responsavel
        from dbo.Itens                    as i
        inner join dbo.OrdensDeServico    as os  on os.nr_os             = i.os_codigo
        inner join dbo.Usuarios           as u   on u.funcionario_codigo = os.usuario_codigo
        inner join dbo.HistoricosStatusItens as hsi on hsi.item_codigo   = i.codigo
        inner join dbo.StatusItens        as si  on si.codigo            = hsi.status_item_codigo
        where i.os_codigo = @nr_os

        union all

        -- 3. eventos de início de etapa de produção no chão de fábrica
        select
            i.os_codigo                                                                 as nr_os,
            irp.data_inicio                                                             as data_hora,
            3                                                                           as ordem_tipo,
            'Etapa de Produção'                                                         as categoria,
            i.codigo                                                                    as item_codigo,
            concat('Item #', i.codigo, ' · ', irp.ordem, 'ª Etapa (', rp.nome, ')')     as referencia,
            concat('Início de ', rp.nome)                                               as evento,
            isnull(p_fun.nome, '-')                                                     as responsavel
        from dbo.ItensRotasProcessos  as irp
        inner join dbo.Itens          as i     on i.codigo     = irp.item_codigo
        inner join dbo.RotasProcessos as rp    on rp.codigo    = irp.rota_processo_codigo
        left join dbo.Pessoas         as p_fun on p_fun.codigo = irp.funcionario_codigo
        where i.os_codigo = @nr_os
          and irp.data_inicio is not null

        union all

        -- 4. eventos de conclusão de etapa de produção com tempo de execução
        select
            i.os_codigo                                                                 as nr_os,
            irp.data_conclusao                                                          as data_hora,
            4                                                                           as ordem_tipo,
            'Etapa de Produção'                                                         as categoria,
            i.codigo                                                                    as item_codigo,
            concat('Item #', i.codigo, ' · ', irp.ordem, 'ª Etapa (', rp.nome, ')')     as referencia,
            concat('Conclusão de ', rp.nome, ' (', datediff(minute, irp.data_inicio, irp.data_conclusao), ' min)') as evento,
            isnull(p_fun.nome, '-')                                                     as responsavel
        from dbo.ItensRotasProcessos  as irp
        inner join dbo.Itens          as i     on i.codigo     = irp.item_codigo
        inner join dbo.RotasProcessos as rp    on rp.codigo    = irp.rota_processo_codigo
        left join dbo.Pessoas         as p_fun on p_fun.codigo = irp.funcionario_codigo
        where i.os_codigo = @nr_os
          and irp.data_conclusao is not null
    ) as eventos
);
go
