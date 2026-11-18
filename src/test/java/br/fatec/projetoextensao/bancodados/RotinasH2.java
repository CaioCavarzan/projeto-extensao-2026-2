package br.fatec.projetoextensao.bancodados;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.h2.tools.SimpleResultSet;

/**
 * INCLUÍDO (18/11/2026) [E3-52]: as 4 functions da Etapa 3 imitadas no H2 dos testes automáticos.
 *
 * Por que existe: os testes rodam no H2 (banco em memória), que não executa T-SQL (OUTER APPLY, TOP, variável
 * tabela...). Para o Java ser testado chamando "dbo.fn_..." exatamente como no SQL Server, o arquivo
 * src/test/resources/etapa3-h2.sql registra estes métodos como funções do H2 (CREATE ALIAS). Cada método devolve as
 * MESMAS colunas, com os mesmos nomes, e aplica as MESMAS regras do script etapa3_01_functions.sql (os comentários
 * apontam o trecho equivalente). O primeiro parâmetro (Connection) é preenchido pelo próprio H2.
 *
 * Funções de tabela: o H2 chama o método duas vezes; na primeira (URL "jdbc:columnlist:connection") só quer saber
 * as colunas, por isso o método devolve o SimpleResultSet sem linhas.
 *
 * ATENÇÃO: isto NÃO substitui o teste no SQL Server de verdade (Etapa3SqlServerIT). É uma imitação para conferir
 * a integração do Java; se o script do grupo mudar, esta classe precisa acompanhar.
 */
public final class RotinasH2 {

    private RotinasH2() {
    }

    private static boolean soColunas(Connection c) throws SQLException {
        return "jdbc:columnlist:connection".equals(c.getMetaData().getURL());
    }

    /** dbo.fn_valor_os(@nr_os): soma de valor_total_item dos itens cujo status atual não é 'Cancelada' (null = 0). */
    public static BigDecimal valorOs(Connection c, Integer nrOs) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                select coalesce(sum(i.valor_total_item), 0)
                from Itens i
                left join dbo.h2_status_item_atual s on s.item_codigo = i.codigo
                where i.os_codigo = ? and coalesce(s.status, '') <> 'Cancelada'
                """)) {
            p.setObject(1, nrOs);
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP);
            }
        }
    }

    /** dbo.fn_itens_atrasados(@data_referencia): prazo antes da data e status atual fora de 'Executada'/'Cancelada'. */
    public static ResultSet itensAtrasados(Connection c, Date referencia) throws SQLException {
        SimpleResultSet r = new SimpleResultSet();
        r.addColumn("item_codigo", Types.INTEGER, 10, 0);
        r.addColumn("nr_os", Types.INTEGER, 10, 0);
        r.addColumn("cliente_nome", Types.VARCHAR, 200, 0);
        r.addColumn("item_descricao", Types.VARCHAR, 100, 0);
        r.addColumn("qtd_total", Types.DECIMAL, 10, 2);
        r.addColumn("unidade", Types.VARCHAR, 10, 0);
        r.addColumn("prazo_entrega", Types.DATE, 10, 0);
        r.addColumn("dias_atraso", Types.INTEGER, 10, 0);
        r.addColumn("status_atual", Types.VARCHAR, 50, 0);
        r.addColumn("etapa_pendente", Types.VARCHAR, 100, 0);
        r.addColumn("operador_etapa", Types.VARCHAR, 200, 0);
        if (soColunas(c)) {
            return r;
        }
        // cross apply do status (item sem histórico não entra) + outer apply da próxima etapa não concluída (top 1
        // por ordem), como no script.
        try (PreparedStatement p = c.prepareStatement("""
                select i.codigo, i.os_codigo, p.nome, i.descricao, i.qtd_total, i.unidade, i.prazo_entrega,
                       datediff(day, i.prazo_entrega, ?) as dias, s.status,
                       (select rp.nome from ItensRotasProcessos irp
                          inner join RotasProcessos rp on rp.codigo = irp.rota_processo_codigo
                        where irp.item_codigo = i.codigo and irp.data_conclusao is null
                        order by irp.ordem fetch first 1 row only) as etapa,
                       (select pf.nome from ItensRotasProcessos irp
                          left join Pessoas pf on pf.codigo = irp.funcionario_codigo
                        where irp.item_codigo = i.codigo and irp.data_conclusao is null
                        order by irp.ordem fetch first 1 row only) as operador
                from Itens i
                inner join OrdensDeServico os on os.nr_os = i.os_codigo
                inner join Pessoas p on p.codigo = os.cliente_codigo
                inner join dbo.h2_status_item_atual s on s.item_codigo = i.codigo
                where i.prazo_entrega < ? and s.status not in ('Executada', 'Cancelada')
                """)) {
            p.setDate(1, referencia);
            p.setDate(2, referencia);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    r.addRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5),
                            rs.getString(6), rs.getDate(7), rs.getInt(8), rs.getString(9), rs.getString(10),
                            rs.getString(11));
                }
            }
        }
        return r;
    }

    /**
     * dbo.fn_prioridade_clientes(@prioridade_minima, @prioridade_maxima): 1ª passada = faturamento de cada cliente
     * (soma de valor_os das OS cujo status atual não é 'Cancelada'); 2ª passada = mínimo + ROUND(faturamento ×
     * amplitude ÷ maior faturamento, 0) quando os dois são maiores que zero, senão o mínimo.
     */
    public static ResultSet prioridadeClientes(Connection c, Integer minima, Integer maxima) throws SQLException {
        SimpleResultSet r = new SimpleResultSet();
        r.addColumn("cliente_codigo", Types.INTEGER, 10, 0);
        r.addColumn("cliente_nome", Types.VARCHAR, 200, 0);
        r.addColumn("faturamento", Types.DECIMAL, 18, 2);
        r.addColumn("prioridade", Types.INTEGER, 10, 0);
        if (soColunas(c)) {
            return r;
        }
        record Faturamento(int cliente, String nome, BigDecimal total) {
        }
        List<Faturamento> lista = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("""
                select cl.pessoa_codigo, pe.nome,
                       coalesce((select sum(os.valor_os) from OrdensDeServico os
                                 left join dbo.h2_status_os_atual st on st.os_codigo = os.nr_os
                                 where os.cliente_codigo = cl.pessoa_codigo and coalesce(st.status, '') <> 'Cancelada'), 0)
                from Clientes cl
                inner join Pessoas pe on pe.codigo = cl.pessoa_codigo
                """); ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                lista.add(new Faturamento(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3)));
            }
        }
        BigDecimal maior = lista.stream().map(Faturamento::total).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal amplitude = BigDecimal.valueOf((long) maxima - minima);
        for (Faturamento f : lista) {
            int prioridade = minima;
            if (maior.signum() > 0 && f.total().signum() > 0) {
                // ROUND(x, 0) do SQL Server = metade para longe do zero (HALF_UP para positivos)
                prioridade = minima + f.total().multiply(amplitude).divide(maior, 0, RoundingMode.HALF_UP).intValue();
            }
            r.addRow(f.cliente(), f.nome(), f.total().setScale(2, RoundingMode.HALF_UP), prioridade);
        }
        return r;
    }

    /**
     * dbo.fn_auditoria_timeline_os(@nr_os): união dos 4 tipos de evento (status da OS, status do item, início e
     * conclusão de etapa), numerada por data_hora, tipo e item, com os mesmos textos do concat() do script.
     */
    public static ResultSet auditoriaTimelineOs(Connection c, Integer nrOs) throws SQLException {
        SimpleResultSet r = new SimpleResultSet();
        r.addColumn("sequencia", Types.BIGINT, 19, 0);
        r.addColumn("nr_os", Types.INTEGER, 10, 0);
        r.addColumn("data_hora", Types.TIMESTAMP, 23, 3);
        r.addColumn("categoria", Types.VARCHAR, 30, 0);
        r.addColumn("item_codigo", Types.INTEGER, 10, 0);
        r.addColumn("referencia", Types.VARCHAR, 300, 0);
        r.addColumn("evento", Types.VARCHAR, 300, 0);
        r.addColumn("responsavel", Types.VARCHAR, 200, 0);
        if (soColunas(c)) {
            return r;
        }
        record Evento(Timestamp dataHora, int ordemTipo, String categoria, Integer item, String referencia, String evento,
                      String responsavel) {
        }
        List<Evento> eventos = new ArrayList<>();
        // 1. status da OS (responsável = login de quem abriu a OS, como no script)
        try (PreparedStatement p = c.prepareStatement("""
                select h.data_hora, s.status, u.login
                from OrdensDeServico os
                inner join Usuarios u on u.funcionario_codigo = os.usuario_codigo
                inner join HistoricosStatusOs h on h.os_codigo = os.nr_os
                inner join StatusOs s on s.codigo = h.status_codigo
                where os.nr_os = ?
                """)) {
            p.setObject(1, nrOs);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    eventos.add(new Evento(rs.getTimestamp(1), 1, "Ordem de Serviço", null, "OS #" + nrOs,
                            "Status da OS: " + rs.getString(2), rs.getString(3)));
                }
            }
        }
        // 2. status dos itens
        try (PreparedStatement p = c.prepareStatement("""
                select h.data_hora, i.codigo, i.descricao, s.status, u.login
                from Itens i
                inner join OrdensDeServico os on os.nr_os = i.os_codigo
                inner join Usuarios u on u.funcionario_codigo = os.usuario_codigo
                inner join HistoricosStatusItens h on h.item_codigo = i.codigo
                inner join StatusItens s on s.codigo = h.status_item_codigo
                where i.os_codigo = ?
                """)) {
            p.setObject(1, nrOs);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    eventos.add(new Evento(rs.getTimestamp(1), 2, "Peça (Item)", rs.getInt(2),
                            "Item #" + rs.getInt(2) + " (" + rs.getString(3) + ")", "Status do Item: " + rs.getString(4),
                            rs.getString(5)));
                }
            }
        }
        // 3 e 4. início e conclusão das etapas (responsável = operador, ou '-')
        try (PreparedStatement p = c.prepareStatement("""
                select i.codigo, irp.ordem, rp.nome, irp.data_inicio, irp.data_conclusao, coalesce(pf.nome, '-'),
                       datediff(minute, irp.data_inicio, irp.data_conclusao)
                from ItensRotasProcessos irp
                inner join Itens i on i.codigo = irp.item_codigo
                inner join RotasProcessos rp on rp.codigo = irp.rota_processo_codigo
                left join Pessoas pf on pf.codigo = irp.funcionario_codigo
                where i.os_codigo = ? and irp.data_inicio is not null
                """)) {
            p.setObject(1, nrOs);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    int item = rs.getInt(1);
                    String etapa = rs.getString(3);
                    String referencia = "Item #" + item + " · " + rs.getInt(2) + "ª Etapa (" + etapa + ")";
                    eventos.add(new Evento(rs.getTimestamp(4), 3, "Etapa de Produção", item, referencia,
                            "Início de " + etapa, rs.getString(6)));
                    if (rs.getTimestamp(5) != null) {
                        eventos.add(new Evento(rs.getTimestamp(5), 4, "Etapa de Produção", item, referencia,
                                "Conclusão de " + etapa + " (" + rs.getInt(7) + " min)", rs.getString(6)));
                    }
                }
            }
        }
        // row_number() over (order by data_hora asc, ordem_tipo asc, isnull(item_codigo, 0) asc)
        eventos.sort(Comparator.comparing(Evento::dataHora).thenComparingInt(Evento::ordemTipo)
                .thenComparingInt(e -> e.item() == null ? 0 : e.item()));
        long sequencia = 0;
        for (Evento e : eventos) {
            r.addRow(++sequencia, nrOs, e.dataHora(), e.categoria(), e.item(), e.referencia(), e.evento(), e.responsavel());
        }
        return r;
    }
}
