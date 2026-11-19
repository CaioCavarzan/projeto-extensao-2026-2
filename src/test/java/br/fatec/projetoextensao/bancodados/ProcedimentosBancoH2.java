package br.fatec.projetoextensao.bancodados;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.fatec.projetoextensao.repositories.ProcedimentosBanco;
import jakarta.persistence.EntityManager;

/**
 * INCLUÍDO (18/11/2026) [E3-52]: as 3 stored procedures da Etapa 3 imitadas para o H2 dos testes automáticos.
 *
 * Por que existe: o H2 não tem stored procedure com parâmetro OUTPUT, então ProcedimentosBancoSqlServer (que usa
 * "{call dbo.sp_...}") não roda nele. Esta classe só existe em src/test, só vale no perfil "test" (@Profile) e tem
 * preferência sobre a do SQL Server (@Primary). Ela faz no banco de teste as MESMAS alterações do script
 * procedures.sql, passo a passo, e recusa com os MESMOS códigos e mensagens (THROW 50001 a 50021), na
 * mesma forma em que o JdbcTemplate entregaria o erro do SQL Server (UncategorizedSQLException com a SQLException
 * dentro). Assim os Services são testados de ponta a ponta, inclusive a tradução dos erros.
 */
@Component
@Primary
@Profile("test")
public class ProcedimentosBancoH2 implements ProcedimentosBanco {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public ProcedimentosBancoH2(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    /** sp_ajustar_estoque_tinta: 50001 quantidade zero; 50002 tinta inexistente; 50003 saldo ficaria negativo. */
    @Override
    public BigDecimal ajustarEstoqueTinta(int tintaCodigo, BigDecimal quantidade) {
        descarregarJpa();
        String chamada = "{call dbo.sp_ajustar_estoque_tinta(?, ?, ?)}";
        if (quantidade == null || quantidade.signum() == 0) {
            throw erro(chamada, 50001, "Informe uma quantidade diferente de zero para ajustar o estoque.");
        }
        List<BigDecimal> saldo = jdbc.queryForList("select estoque_atual from Tintas where codigo = ? for update",
                BigDecimal.class, tintaCodigo);
        if (saldo.isEmpty() || saldo.get(0) == null) {
            throw erro(chamada, 50002, "Tinta não encontrada.");
        }
        BigDecimal resultante = saldo.get(0).add(quantidade);
        if (resultante.signum() < 0) {
            throw erro(chamada, 50003, "Estoque insuficiente para realizar a baixa.");
        }
        jdbc.update("update Tintas set estoque_atual = ? where codigo = ?", resultante, tintaCodigo);
        return resultante.setScale(2);
    }

    /**
     * sp_cancelar_ordem_servico: 50010 OS inexistente; 50011 concluída; 50012 já cancelada. Depois: 'Cancelada' nos
     * itens abertos (data_hora = agora, ou última + 1 s), apaga as etapas não concluídas desses itens dos cronogramas
     * de hoje em diante, 'Cancelada' na OS e valor_os = fn_valor_os.
     */
    @Override
    public int cancelarOrdemServico(int numeroOs) {
        descarregarJpa();
        String chamada = "{call dbo.sp_cancelar_ordem_servico(?, ?)}";
        Integer existe = jdbc.queryForObject("select count(*) from OrdensDeServico where nr_os = ?", Integer.class, numeroOs);
        if (existe == null || existe == 0) {
            throw erro(chamada, 50010, "Ordem de serviço não encontrada.");
        }
        List<Map<String, Object>> statusOs = jdbc.queryForList(
                "select status, data_hora from dbo.h2_status_os_atual where os_codigo = ?", numeroOs);
        String atual = statusOs.isEmpty() ? null : (String) statusOs.get(0).get("status");
        Timestamp ultimaOs = statusOs.isEmpty() ? null : (Timestamp) statusOs.get(0).get("data_hora");
        if (atual != null && atual.matches("Conclu.da")) {
            throw erro(chamada, 50011, "OS concluída não pode ser cancelada.");
        }
        if ("Cancelada".equals(atual)) {
            throw erro(chamada, 50012, "A OS já está cancelada.");
        }
        Integer cancelOs = jdbc.queryForObject("select codigo from StatusOs where status = 'Cancelada'", Integer.class);
        Integer cancelItem = jdbc.queryForObject("select codigo from StatusItens where status = 'Cancelada'", Integer.class);
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        List<Map<String, Object>> abertos = jdbc.queryForList("""
                select i.codigo, s.data_hora from Itens i
                inner join dbo.h2_status_item_atual s on s.item_codigo = i.codigo
                where i.os_codigo = ? and s.status not in ('Executada', 'Cancelada')
                """, numeroOs);
        for (Map<String, Object> item : abertos) {
            jdbc.update("insert into HistoricosStatusItens (item_codigo, status_item_codigo, data_hora) values (?, ?, ?)",
                    item.get("codigo"), cancelItem, proxima(agora, (Timestamp) item.get("data_hora")));
        }
        jdbc.update("""
                delete from CronogramasItens ci where exists (
                    select 1 from Cronogramas c
                    inner join ItensRotasProcessos irp on irp.codigo = ci.item_rota_processo_codigo
                    inner join Itens i on i.codigo = irp.item_codigo
                    where c.codigo = ci.cronograma_codigo and i.os_codigo = ? and irp.data_conclusao is null
                      and c.data_planejada >= current_date)
                """, numeroOs);
        jdbc.update("insert into HistoricosStatusOs (os_codigo, status_codigo, data_hora) values (?, ?, ?)",
                numeroOs, cancelOs, proxima(agora, ultimaOs));
        jdbc.update("update OrdensDeServico set valor_os = dbo.fn_valor_os(?) where nr_os = ?", numeroOs, numeroOs);
        return abertos.size();
    }

    /**
     * sp_replanejar_cronograma: 50020 datas inválidas; 50021 usuário inválido ou inativo. Sem cronograma na origem:
     * 0. Cria o cronograma do destino se não existir e copia para o fim da fila as etapas pendentes da origem.
     */
    @Override
    public int replanejarCronograma(LocalDate dataOrigem, LocalDate dataDestino, int usuarioCodigo) {
        descarregarJpa();
        String chamada = "{call dbo.sp_replanejar_cronograma(?, ?, ?, ?)}";
        if (dataOrigem == null || dataDestino == null || !dataDestino.isAfter(dataOrigem)) {
            throw erro(chamada, 50020, "A data de destino deve ser posterior à data de origem.");
        }
        Integer ativo = jdbc.queryForObject("select count(*) from Usuarios where funcionario_codigo = ? and ativo = true",
                Integer.class, usuarioCodigo);
        if (ativo == null || ativo == 0) {
            throw erro(chamada, 50021, "Usuário inválido ou inativo.");
        }
        Integer origem = jdbc.queryForObject("select min(codigo) from Cronogramas where data_planejada = ?", Integer.class,
                Date.valueOf(dataOrigem));
        if (origem == null) {
            return 0;
        }
        Integer destino = jdbc.queryForObject("select min(codigo) from Cronogramas where data_planejada = ?", Integer.class,
                Date.valueOf(dataDestino));
        if (destino == null) {
            GeneratedKeyHolder chave = new GeneratedKeyHolder();
            jdbc.update(c -> {
                PreparedStatement p = c.prepareStatement(
                        "insert into Cronogramas (usuario_codigo, data_planejada, observacao) values (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                p.setInt(1, usuarioCodigo);
                p.setDate(2, Date.valueOf(dataDestino));
                p.setString(3, "Replanejado a partir de " + dataOrigem); // convert(varchar(10), @data_origem, 23)
                return p;
            }, chave);
            destino = chave.getKey().intValue();
        }
        Integer proxima = jdbc.queryForObject(
                "select coalesce(max(posicao) + 1, 0) from CronogramasItens where cronograma_codigo = ?", Integer.class, destino);
        List<Map<String, Object>> pendentes = jdbc.queryForList("""
                select ci.item_rota_processo_codigo, ci.qtd_planejado, ci.pontuacao
                from CronogramasItens ci
                inner join ItensRotasProcessos irp on irp.codigo = ci.item_rota_processo_codigo
                inner join Itens i on i.codigo = irp.item_codigo
                inner join dbo.h2_status_item_atual si on si.item_codigo = i.codigo
                inner join dbo.h2_status_os_atual so on so.os_codigo = i.os_codigo
                where ci.cronograma_codigo = ?
                  and irp.data_conclusao is null
                  and si.status not in ('Executada', 'Cancelada')
                  and so.status not like 'Conclu_da'
                  and so.status <> 'Cancelada'
                  and not exists (select 1 from CronogramasItens d
                                  where d.cronograma_codigo = ? and d.item_rota_processo_codigo = ci.item_rota_processo_codigo)
                order by ci.posicao
                """, origem, destino);
        int deslocamento = 0;
        for (Map<String, Object> e : pendentes) {
            jdbc.update("""
                    insert into CronogramasItens (cronograma_codigo, item_rota_processo_codigo, qtd_planejado, posicao, pontuacao)
                    values (?, ?, ?, ?, ?)
                    """, destino, e.get("item_rota_processo_codigo"), e.get("qtd_planejado"), proxima + deslocamento++,
                    e.get("pontuacao"));
        }
        return pendentes.size();
    }

    // ------------------------------------------------------------------ apoio

    /** Mesma regra das procedures: se a última data_hora já é >= agora, usa a última + 1 segundo. */
    private static Timestamp proxima(LocalDateTime agora, Timestamp ultima) {
        if (ultima != null && !agora.isAfter(ultima.toLocalDateTime())) {
            return Timestamp.valueOf(ultima.toLocalDateTime().plusSeconds(1));
        }
        return Timestamp.valueOf(agora);
    }

    /** O erro como o JdbcTemplate entregaria um THROW do SQL Server. */
    private static UncategorizedSQLException erro(String chamada, int codigo, String mensagem) {
        return new UncategorizedSQLException("CallableStatementCallback", chamada,
                new SQLException(mensagem, "S0001", codigo));
    }

    private void descarregarJpa() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            entityManager.flush();
        }
    }
}
