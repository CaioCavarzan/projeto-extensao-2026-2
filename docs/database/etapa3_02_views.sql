-- =================================================================================================
-- arquivo : docs/database/etapa3_02_views.sql
-- banco   : banco_fenix (sql server)
-- ordem   : 2º script da etapa 3
-- objetos : 1. dbo.vw_painel_ordens_servico
--           2. dbo.vw_produtividade_funcionario
--           3. dbo.vw_retrabalho_mensal
-- =================================================================================================

use banco_fenix
go

-- -------------------------------------------------------------------------------------------------
-- 1. view: dbo.vw_painel_ordens_servico
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
-- 2. view: dbo.vw_produtividade_funcionario
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
-- 3. view: dbo.vw_retrabalho_mensal
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
