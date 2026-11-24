package br.fatec.projetoextensao.bancodados;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.h2.api.Trigger;

/**
 * INCLUÍDO (18/11/2026) [E3-52]: os 3 triggers da Etapa 3 imitados no H2 dos testes automáticos (registrados por
 * src/test/resources/etapa3-h2.sql com CREATE TRIGGER ... FOR EACH ROW CALL).
 *
 * Diferença de forma, não de regra: no SQL Server o trigger roda uma vez por comando e lê as tabelas lógicas
 * inserted/deleted; no H2 o trigger roda uma vez por linha e recebe a linha antiga e a nova. Para INSERT, UPDATE e
 * DELETE de uma linha (o que o Java faz) o efeito é o mesmo. Os erros saem com o MESMO código e a MESMA mensagem do
 * THROW do script (SQLException com errorCode 50110, 50101, 50102 e 50120), para os testes conferirem a tradução
 * feita por ErrosBanco.
 */
public final class TriggersH2 {

    private TriggersH2() {
    }

    /** Posição (0, 1, 2...) de cada coluna na linha que o H2 entrega ao trigger. */
    private static Map<String, Integer> colunas(Connection c, String schema, String tabela) throws SQLException {
        Map<String, Integer> posicoes = new HashMap<>();
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(null, schema, tabela, null)) {
            while (rs.next()) {
                posicoes.put(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), rs.getInt("ORDINAL_POSITION") - 1);
            }
        }
        return posicoes;
    }

    private static Object unico(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                p.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    private static void executar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                p.setObject(i + 1, parametros[i]);
            }
            p.executeUpdate();
        }
    }

    /** Erro no formato do THROW do SQL Server (código >= 50000, estado genérico). */
    static SQLException erro(int codigo, String mensagem) {
        return new SQLException(mensagem, "45000", codigo);
    }

    private static void atualizarValorOs(Connection c, Object nrOs) throws SQLException {
        if (nrOs != null) {
            executar(c, "update OrdensDeServico set valor_os = dbo.fn_valor_os(?) where nr_os = ?", nrOs, nrOs);
        }
    }

    /**
     * trg_itens_sincronizar_valor_os (after insert, update, delete on Itens): (1) cor do item diferente da cor da
     * tinta -> THROW 50110; (2) recalcula valor_os da OS da linha nova e da antiga.
     */
    public static class ItensSincronizarValorOs implements Trigger {

        private Map<String, Integer> col;

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                         int type) throws SQLException {
            col = colunas(conn, schemaName, tableName);
        }

        @Override
        public void fire(Connection c, Object[] antiga, Object[] nova) throws SQLException {
            if (nova != null && nova[col.get("tinta_codigo")] != null) {
                Object corTinta = unico(c, "select cor_codigo from Tintas where codigo = ?", nova[col.get("tinta_codigo")]);
                Object corItem = nova[col.get("cor_codigo")];
                // no T-SQL: ins.cor_codigo <> t.cor_codigo (com NULL a comparação não é verdadeira)
                if (corTinta != null && corItem != null && ((Number) corItem).intValue() != ((Number) corTinta).intValue()) {
                    throw erro(50110, "Inconsistência no item: a cor informada diverge da cor cadastrada na tinta.");
                }
            }
            Object osNova = nova == null ? null : nova[col.get("os_codigo")];
            Object osAntiga = antiga == null ? null : antiga[col.get("os_codigo")];
            atualizarValorOs(c, osNova);
            if (osAntiga != null && !Objects.equals(osAntiga, osNova)) {
                atualizarValorOs(c, osAntiga);
            }
        }
    }

    /**
     * trg_historicos_status_os_proteger (after insert, update, delete on HistoricosStatusOs): (1) UPDATE/DELETE ->
     * THROW 50101; (2) status anterior (o mais recente até a data_hora nova, sem contar a própria linha) 'Concluída'
     * ou 'Cancelada' -> THROW 50102.
     */
    public static class HistoricosStatusOsProteger implements Trigger {

        private Map<String, Integer> col;

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                         int type) throws SQLException {
            col = colunas(conn, schemaName, tableName);
        }

        @Override
        public void fire(Connection c, Object[] antiga, Object[] nova) throws SQLException {
            if (antiga != null) {
                throw erro(50101, "O histórico de status da OS é imutável: não são permitidos UPDATE ou DELETE.");
            }
            Object os = nova[col.get("os_codigo")];
            Object status = nova[col.get("status_codigo")];
            Object dataHora = nova[col.get("data_hora")];
            Object anterior = unico(c, """
                    select s.status from HistoricosStatusOs h
                    inner join StatusOs s on s.codigo = h.status_codigo
                    where h.os_codigo = ? and not (h.status_codigo = ? and h.data_hora = ?) and h.data_hora <= ?
                    order by h.data_hora desc fetch first 1 row only
                    """, os, status, dataHora, dataHora);
            if (anterior != null) {
                String s = anterior.toString();
                if (s.matches("(?i)Conclu.da") || s.equalsIgnoreCase("Cancelada")) {
                    throw erro(50102, "OS encerrada (Concluída ou Cancelada) não aceita novas mudanças de status.");
                }
            }
        }
    }

    /**
     * trg_historicos_status_itens_sincronizar_item_e_os (after insert, update, delete on HistoricosStatusItens):
     * (1) UPDATE/DELETE -> THROW 50120; (2) status 'Executada' preenche Itens.data_finalizacao (a data_hora do
     * histórico, ou a data_entrada se for anterior a ela) quando ainda está vazia; (3) recalcula valor_os da OS.
     */
    public static class HistoricosStatusItensSincronizar implements Trigger {

        private Map<String, Integer> col;

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                         int type) throws SQLException {
            col = colunas(conn, schemaName, tableName);
        }

        @Override
        public void fire(Connection c, Object[] antiga, Object[] nova) throws SQLException {
            if (antiga != null) {
                throw erro(50120, "O histórico de status do item é imutável: não são permitidos UPDATE ou DELETE.");
            }
            Object item = nova[col.get("item_codigo")];
            Object dataHora = nova[col.get("data_hora")];
            Object status = unico(c, "select status from StatusItens where codigo = ?", nova[col.get("status_item_codigo")]);
            if ("Executada".equals(status)) {
                executar(c, """
                        update Itens set data_finalizacao = case when ? < data_entrada then data_entrada else ? end
                        where codigo = ? and data_finalizacao is null
                        """, dataHora, dataHora, item);
            }
            atualizarValorOs(c, unico(c, "select os_codigo from Itens where codigo = ?", item));
        }
    }
}
