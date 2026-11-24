-- =================================================================================================
-- arquivo  : docs/database/objetos_banco.sql
-- banco    : banco_fenix (sql server 2016 sp1 ou mais recente, por causa do "create or alter")
-- pré-req. : schema.sql (cria o banco e as 22 tabelas) e, para demonstrar, data.sql (carga)
-- conteúdo : os 13 objetos programáveis do banco, reunidos em um único script, na ordem em que
--            precisam ser criados:
--
--   parte 1 - functions (4)
--     1.1 dbo.fn_valor_os                                    (scalar function)
--     1.2 dbo.fn_itens_atrasados                             (inline table-valued function)
--     1.3 dbo.fn_prioridade_clientes                         (multi-statement table-valued function)
--     1.4 dbo.fn_auditoria_timeline_os                       (inline table-valued function)
--
--   parte 2 - views (3)
--     2.1 dbo.vw_painel_ordens_servico
--     2.2 dbo.vw_produtividade_funcionario
--     2.3 dbo.vw_retrabalho_mensal
--
--   parte 3 - stored procedures (3), todas com transação explícita
--             (begin tran / commit tran / rollback tran + set xact_abort on + try/catch)
--     3.1 dbo.sp_ajustar_estoque_tinta
--     3.2 dbo.sp_cancelar_ordem_servico
--     3.3 dbo.sp_replanejar_cronograma
--
--   parte 4 - triggers (3)
--     4.1 dbo.trg_itens_sincronizar_valor_os                 (dbo.Itens)
--     4.2 dbo.trg_historicos_status_os_proteger              (dbo.HistoricosStatusOs)
--     4.3 dbo.trg_historicos_status_itens_sincronizar_item_e_os (dbo.HistoricosStatusItens)
--
-- por que esta ordem: as views, as procedures e os triggers chamam dbo.fn_valor_os, então as
-- functions precisam existir antes deles.
--
-- como rodar no ssms: abrir este arquivo e executar tudo (f5). todos os objetos usam
-- "create or alter": rodar de novo só atualiza os objetos, sem apagar dados.
--
-- uso pela aplicação: a tela configurações > banco de dados > "instalar ou atualizar objetos"
-- executa este mesmo arquivo (BancoDadosService). o arquivo é dividido nas linhas "go", o lote
-- "use" é pulado e cada um dos 13 lotes seguintes cria um objeto.
-- regra para quem editar: manter um único "use banco_fenix" no início e exatamente um objeto
-- "create or alter" por lote (um "go" depois de cada objeto). um teste automático confere isso.
-- =================================================================================================

use banco_fenix
go

-- #################################################################################################
-- parte 1 - functions (4 objetos)
-- pré-requisito das partes 2, 3 e 4 (dbo.fn_valor_os é usada por views, procedures e triggers).
-- #################################################################################################

-- -------------------------------------------------------------------------------------------------
-- 1.1 função escalar: dbo.fn_valor_os
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
-- 1.2 função inline table-valued: dbo.fn_itens_atrasados
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
-- 1.3 função multi-statement table-valued (com variável tabela): dbo.fn_prioridade_clientes
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
-- 1.4 função inline table-valued: dbo.fn_auditoria_timeline_os
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

-- #################################################################################################
-- parte 2 - views (3 objetos)
-- consultas prontas usadas pelo dashboard, pelas listas de os, pelos indicadores e pelos relatórios.
-- #################################################################################################

-- -------------------------------------------------------------------------------------------------
-- 2.1 view: dbo.vw_painel_ordens_servico
-- tabelas base : dbo.OrdensDeServico, dbo.Clientes, dbo.Pessoas, dbo.PessoasFisicas,
--                dbo.PessoasJuridicas, dbo.Usuarios, dbo.HistoricosStatusOs, dbo.StatusOs,
--                dbo.Itens, dbo.HistoricosStatusItens, dbo.StatusItens
-- finalidade   : consolida o painel gerencial de ordens de serviço em uma única consulta, trazendo
--                dados do cliente ('PF'/'PJ' e documento), login de abertura, status atual da os e
--                contagens agregadas dos itens (total, executados, cancelados, em aberto, atrasados
--                e indicador de valor incompleto).
-- -------------------------------------------------------------------------------------------------
create or alter view dbo.vw_painel_ordens_servico
as
select
    os.nr_os,
    os.data_entrada,
    os.cliente_codigo,
    p_cli.nome                                                        as cliente_nome,
    case when pf.cliente_codigo is not null then 'PF' else 'PJ' end   as tipo_cliente,
    coalesce(pf.cpf, pj.cnpj)                                         as documento_cliente,
    u.login                                                           as aberta_por,
    os.prioridade,
    os.valor_os,
    status_os.status                                                  as status_atual,
    status_os.data_hora                                               as status_desde,
    isnull(resumo_itens.qtd_itens, 0)                                 as qtd_itens,
    isnull(resumo_itens.qtd_itens_executados, 0)                      as qtd_itens_executados,
    isnull(resumo_itens.qtd_itens_cancelados, 0)                      as qtd_itens_cancelados,
    isnull(resumo_itens.qtd_itens_em_aberto, 0)                       as qtd_itens_em_aberto,
    isnull(resumo_itens.qtd_itens_atrasados, 0)                       as qtd_itens_atrasados,
    cast(isnull(resumo_itens.valor_incompleto, 0) as bit)             as valor_incompleto
from dbo.OrdensDeServico       as os
inner join dbo.Clientes        as c     on c.pessoa_codigo      = os.cliente_codigo
inner join dbo.Pessoas         as p_cli on p_cli.codigo         = c.pessoa_codigo
inner join dbo.Usuarios        as u     on u.funcionario_codigo = os.usuario_codigo
left join dbo.PessoasFisicas   as pf    on pf.cliente_codigo    = c.pessoa_codigo
left join dbo.PessoasJuridicas as pj    on pj.cliente_codigo    = c.pessoa_codigo
outer apply (
    select top (1)
        so.status,
        hso.data_hora
    from dbo.HistoricosStatusOs as hso
    inner join dbo.StatusOs     as so on so.codigo = hso.status_codigo
    where hso.os_codigo = os.nr_os
    order by hso.data_hora desc
) as status_os
outer apply (
    select
        count(*)                                                                    as qtd_itens,
        sum(case when st.status = 'Executada' then 1 else 0 end)                    as qtd_itens_executados,
        sum(case when st.status = 'Cancelada' then 1 else 0 end)                    as qtd_itens_cancelados,
        sum(case when st.status not in ('Executada', 'Cancelada') then 1 else 0 end) as qtd_itens_em_aberto,
        sum(case when st.status not in ('Executada', 'Cancelada')
                  and i.prazo_entrega < cast(sysdatetime() as date)
                 then 1 else 0 end)                                                 as qtd_itens_atrasados,
        max(case when st.status <> 'Cancelada' and i.valor_total_item is null
                 then 1 else 0 end)                                                 as valor_incompleto
    from dbo.Itens as i
    outer apply (
        select top (1) si.status
        from dbo.HistoricosStatusItens as hsi
        inner join dbo.StatusItens     as si on si.codigo = hsi.status_item_codigo
        where hsi.item_codigo = i.codigo
        order by hsi.data_hora desc
    ) as st
    where i.os_codigo = os.nr_os
) as resumo_itens;
go

-- -------------------------------------------------------------------------------------------------
-- 2.2 view: dbo.vw_produtividade_funcionario
-- tabelas base : dbo.ItensRotasProcessos, dbo.Funcionarios, dbo.Pessoas, dbo.RotasProcessos
-- finalidade   : agrupa as etapas de produção concluídas por data, funcionário e processo
--                ('Jateamento', 'Banho', 'Pintura'), fornecendo a quantidade de etapas concluídas,
--                peças distintas atendidas, quantas etapas foram retrabalho, minutos totais
--                trabalhados e tempo médio por etapa.
-- -------------------------------------------------------------------------------------------------
create or alter view dbo.vw_produtividade_funcionario
as
select
    cast(irp.data_conclusao as date)                                            as data_producao,
    irp.funcionario_codigo,
    p_fun.nome                                                                  as funcionario_nome,
    f.cargo,
    rp.nome                                                                     as processo,
    count(*)                                                                    as etapas_concluidas,
    count(distinct irp.item_codigo)                                             as itens_atendidos,
    sum(ind_retrabalho.eh_retrabalho)                                           as etapas_retrabalho,
    sum(datediff(minute, irp.data_inicio, irp.data_conclusao))                  as minutos_trabalhados,
    cast(avg(cast(datediff(minute, irp.data_inicio, irp.data_conclusao) as decimal(10, 2)))
         as decimal(10, 2))                                                     as minutos_medios_por_etapa
from dbo.ItensRotasProcessos   as irp
inner join dbo.Funcionarios    as f     on f.pessoa_codigo = irp.funcionario_codigo
inner join dbo.Pessoas         as p_fun on p_fun.codigo    = f.pessoa_codigo
inner join dbo.RotasProcessos  as rp    on rp.codigo       = irp.rota_processo_codigo
cross apply (
    select case when exists (
        select 1
        from dbo.ItensRotasProcessos as ant
        where ant.item_codigo          = irp.item_codigo
          and ant.rota_processo_codigo = irp.rota_processo_codigo
          and ant.ordem                < irp.ordem
    ) then 1 else 0 end as eh_retrabalho
) as ind_retrabalho
where irp.data_conclusao is not null
group by
    cast(irp.data_conclusao as date),
    irp.funcionario_codigo,
    p_fun.nome,
    f.cargo,
    rp.nome;
go

-- -------------------------------------------------------------------------------------------------
-- 2.3 view: dbo.vw_retrabalho_mensal
-- tabelas base : dbo.Itens, dbo.ItensRotasProcessos, dbo.HistoricosStatusItens, dbo.StatusItens
-- finalidade   : calcula o indicador mensal de qualidade (pdca): agrupa os itens com status atual
--                'Executada' pelo mês de sua data_finalizacao e apura o total de itens executados,
--                quantos apresentaram repetição de etapa (retrabalho), o percentual de retrabalho
--                (%) e o valor financeiro total executado no período.
-- -------------------------------------------------------------------------------------------------
create or alter view dbo.vw_retrabalho_mensal
as
with cte_itens_executados as (
    select
        datefromparts(year(i.data_finalizacao), month(i.data_finalizacao), 1) as mes_referencia,
        i.valor_total_item,
        case when exists (
            select 1
            from dbo.ItensRotasProcessos as irp
            where irp.item_codigo = i.codigo
            group by irp.rota_processo_codigo
            having count(*) > 1
        ) then 1 else 0 end as teve_retrabalho
    from dbo.Itens as i
    cross apply (
        select top (1) si.status
        from dbo.HistoricosStatusItens as hsi
        inner join dbo.StatusItens     as si on si.codigo = hsi.status_item_codigo
        where hsi.item_codigo = i.codigo
        order by hsi.data_hora desc
    ) as status_item
    where i.data_finalizacao is not null
      and status_item.status = 'Executada'
)
select
    mes_referencia,
    year(mes_referencia)                                                as ano,
    month(mes_referencia)                                               as mes,
    count(*)                                                            as itens_executados,
    sum(teve_retrabalho)                                                as itens_com_retrabalho,
    cast((100.0 * sum(teve_retrabalho)) / count(*) as decimal(5, 2))    as percentual_retrabalho,
    isnull(sum(valor_total_item), 0.00)                                 as valor_executado
from cte_itens_executados
group by mes_referencia;
go

-- #################################################################################################
-- parte 3 - stored procedures (3 objetos)
-- operações de escrita com transação explícita; dependem de dbo.fn_valor_os.
-- #################################################################################################

-- -------------------------------------------------------------------------------------------------
-- 3.1 stored procedure: dbo.sp_ajustar_estoque_tinta
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
-- 3.2 stored procedure: dbo.sp_cancelar_ordem_servico
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
-- 3.3 stored procedure: dbo.sp_replanejar_cronograma
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

-- #################################################################################################
-- parte 4 - triggers (3 objetos)
-- regras garantidas pelo próprio banco a cada insert, update ou delete; dependem de dbo.fn_valor_os.
-- #################################################################################################

-- -------------------------------------------------------------------------------------------------
-- 4.1 trigger: dbo.trg_itens_sincronizar_valor_os
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
-- 4.2 trigger: dbo.trg_historicos_status_os_proteger
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
-- 4.3 trigger: dbo.trg_historicos_status_itens_sincronizar_item_e_os
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
