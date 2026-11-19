-- INCLUÍDO (18/11/2026) [E3-51]: objetos da Etapa 3 imitados no H2 dos testes automáticos (perfil "test").
-- Roda depois do schema-h2.sql (application-test.properties). O H2 não executa T-SQL (OUTER/CROSS APPLY, TOP,
-- variável tabela, THROW, inserted/deleted), então cada objeto do SQL Server tem aqui uma versão com o MESMO nome
-- (schema dbo), as MESMAS colunas e as MESMAS regras, escrita no SQL do H2 ou em Java:
--   * functions  -> CREATE ALIAS para métodos de br.fatec.projetoextensao.bancodados.RotinasH2;
--   * views      -> CREATE VIEW com subconsultas no lugar do APPLY;
--   * triggers   -> CREATE TRIGGER ... FOR EACH ROW CALL classes de TriggersH2;
--   * procedures -> não ficam no banco: a classe ProcedimentosBancoH2 (perfil test) faz o papel delas.
-- As duas views h2_status_* existem SÓ aqui: são o "status atual" (registro mais recente do histórico, RN-HOS-03
-- e RN-HIT-03), que no T-SQL é o OUTER APPLY (select top (1) ... order by data_hora desc).
-- Regra deste arquivo: nenhum ";" dentro de um comando (o Spring separa os comandos pelo ";").
-- ATENÇÃO: isto não substitui o teste no SQL Server de verdade (Etapa3SqlServerIT, ver docs/database/INTEGRACAO_ETAPA3_JAVA.md).

create schema if not exists dbo;

-- status atual de cada item e de cada OS
create view dbo.h2_status_item_atual as
select h.item_codigo, s.status, h.data_hora
from HistoricosStatusItens h
inner join StatusItens s on s.codigo = h.status_item_codigo
where not exists (select 1 from HistoricosStatusItens h2 where h2.item_codigo = h.item_codigo and h2.data_hora > h.data_hora);

create view dbo.h2_status_os_atual as
select h.os_codigo, s.status, h.data_hora
from HistoricosStatusOs h
inner join StatusOs s on s.codigo = h.status_codigo
where not exists (select 1 from HistoricosStatusOs h2 where h2.os_codigo = h.os_codigo and h2.data_hora > h.data_hora);

-- ---------------------------------------------------------------- functions (functions.sql)
create alias dbo.fn_valor_os for 'br.fatec.projetoextensao.bancodados.RotinasH2.valorOs';
create alias dbo.fn_itens_atrasados for 'br.fatec.projetoextensao.bancodados.RotinasH2.itensAtrasados';
create alias dbo.fn_prioridade_clientes for 'br.fatec.projetoextensao.bancodados.RotinasH2.prioridadeClientes';
create alias dbo.fn_auditoria_timeline_os for 'br.fatec.projetoextensao.bancodados.RotinasH2.auditoriaTimelineOs';

-- ---------------------------------------------------------------- views (views.sql)
create view dbo.vw_painel_ordens_servico as
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
    st.status                                                         as status_atual,
    st.data_hora                                                      as status_desde,
    coalesce(r.qtd_itens, 0)                                          as qtd_itens,
    coalesce(r.qtd_itens_executados, 0)                               as qtd_itens_executados,
    coalesce(r.qtd_itens_cancelados, 0)                               as qtd_itens_cancelados,
    coalesce(r.qtd_itens_em_aberto, 0)                                as qtd_itens_em_aberto,
    coalesce(r.qtd_itens_atrasados, 0)                                as qtd_itens_atrasados,
    coalesce(r.valor_incompleto, 0) = 1                               as valor_incompleto
from OrdensDeServico os
inner join Clientes c        on c.pessoa_codigo      = os.cliente_codigo
inner join Pessoas p_cli     on p_cli.codigo         = c.pessoa_codigo
inner join Usuarios u        on u.funcionario_codigo = os.usuario_codigo
left join PessoasFisicas pf  on pf.cliente_codigo    = c.pessoa_codigo
left join PessoasJuridicas pj on pj.cliente_codigo   = c.pessoa_codigo
left join dbo.h2_status_os_atual st on st.os_codigo  = os.nr_os
left join (
    select
        i.os_codigo,
        count(*)                                                                     as qtd_itens,
        sum(case when s.status = 'Executada' then 1 else 0 end)                      as qtd_itens_executados,
        sum(case when s.status = 'Cancelada' then 1 else 0 end)                      as qtd_itens_cancelados,
        sum(case when s.status not in ('Executada', 'Cancelada') then 1 else 0 end)  as qtd_itens_em_aberto,
        sum(case when s.status not in ('Executada', 'Cancelada')
                  and i.prazo_entrega < current_date then 1 else 0 end)               as qtd_itens_atrasados,
        max(case when s.status <> 'Cancelada' and i.valor_total_item is null
                 then 1 else 0 end)                                                  as valor_incompleto
    from Itens i
    left join dbo.h2_status_item_atual s on s.item_codigo = i.codigo
    group by i.os_codigo
) r on r.os_codigo = os.nr_os;

create view dbo.vw_produtividade_funcionario as
select
    cast(x.data_conclusao as date)                                          as data_producao,
    x.funcionario_codigo,
    p_fun.nome                                                              as funcionario_nome,
    f.cargo,
    rp.nome                                                                 as processo,
    count(*)                                                                as etapas_concluidas,
    count(distinct x.item_codigo)                                           as itens_atendidos,
    sum(x.eh_retrabalho)                                                    as etapas_retrabalho,
    sum(datediff(minute, x.data_inicio, x.data_conclusao))                  as minutos_trabalhados,
    cast(avg(cast(datediff(minute, x.data_inicio, x.data_conclusao) as decimal(10, 2))) as decimal(10, 2))
                                                                            as minutos_medios_por_etapa
from (
    select irp.*,
           case when exists (
               select 1 from ItensRotasProcessos ant
               where ant.item_codigo = irp.item_codigo
                 and ant.rota_processo_codigo = irp.rota_processo_codigo
                 and ant.ordem < irp.ordem
           ) then 1 else 0 end as eh_retrabalho
    from ItensRotasProcessos irp
    where irp.data_conclusao is not null
) x
inner join Funcionarios f   on f.pessoa_codigo = x.funcionario_codigo
inner join Pessoas p_fun    on p_fun.codigo    = f.pessoa_codigo
inner join RotasProcessos rp on rp.codigo      = x.rota_processo_codigo
group by cast(x.data_conclusao as date), x.funcionario_codigo, p_fun.nome, f.cargo, rp.nome;

create view dbo.vw_retrabalho_mensal as
select
    x.mes_referencia,
    year(x.mes_referencia)                                                         as ano,
    month(x.mes_referencia)                                                        as mes,
    count(*)                                                                       as itens_executados,
    sum(x.teve_retrabalho)                                                         as itens_com_retrabalho,
    cast(cast(100.0 * sum(x.teve_retrabalho) as decimal(20, 10)) / count(*) as decimal(5, 2)) as percentual_retrabalho,
    coalesce(sum(x.valor_total_item), 0.00)                                        as valor_executado
from (
    select
        cast(date_trunc('MONTH', i.data_finalizacao) as date) as mes_referencia,
        i.valor_total_item,
        case when exists (
            select 1 from ItensRotasProcessos irp
            where irp.item_codigo = i.codigo
            group by irp.rota_processo_codigo
            having count(*) > 1
        ) then 1 else 0 end as teve_retrabalho
    from Itens i
    inner join dbo.h2_status_item_atual s on s.item_codigo = i.codigo
    where i.data_finalizacao is not null and s.status = 'Executada'
) x
group by x.mes_referencia;

-- ---------------------------------------------------------------- triggers (triggers.sql)
create trigger trg_itens_sincronizar_valor_os after insert, update, delete on Itens
for each row call 'br.fatec.projetoextensao.bancodados.TriggersH2$ItensSincronizarValorOs';

create trigger trg_historicos_status_os_proteger after insert, update, delete on HistoricosStatusOs
for each row call 'br.fatec.projetoextensao.bancodados.TriggersH2$HistoricosStatusOsProteger';

create trigger trg_historicos_status_itens_sincronizar_item_e_os after insert, update, delete on HistoricosStatusItens
for each row call 'br.fatec.projetoextensao.bancodados.TriggersH2$HistoricosStatusItensSincronizar';
