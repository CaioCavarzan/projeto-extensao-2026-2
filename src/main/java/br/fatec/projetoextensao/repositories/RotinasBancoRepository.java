package br.fatec.projetoextensao.repositories;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.fatec.projetoextensao.dtos.EventoAuditoriaResponse;
import br.fatec.projetoextensao.dtos.ItemAtrasadoResponse;
import br.fatec.projetoextensao.dtos.PainelOsResponse;
import br.fatec.projetoextensao.dtos.ProdutividadeResponse;
import br.fatec.projetoextensao.dtos.RetrabalhoMensalResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (18/11/2026) [E3-05]: leitura das 4 functions e das 3 views da Etapa 3 (scripts etapa3_01_functions.sql e
 * etapa3_02_views.sql). É o único lugar do Java que escreve SQL para esses objetos.
 *
 * Por que JdbcClient (SQL "puro") e não um repository do Spring Data:
 * <ul>
 *   <li>as functions de tabela (fn_itens_atrasados, fn_prioridade_clientes, fn_auditoria_timeline_os) e as views
 *       devolvem linhas que não são entidades: não têm @Entity nem chave, são relatórios prontos;</li>
 *   <li>cada linha vira direto o DTO que a API devolve (ItemAtrasadoResponse, PainelOsResponse...), sem passar por
 *       entidade e sem o Hibernate guardar nada na memória da transação.</li>
 * </ul>
 * O JdbcClient usa a MESMA conexão e a MESMA transação do JPA (o JpaTransactionManager do Spring Boot entrega a
 * conexão do Hibernate ao JDBC), então a consulta enxerga o que a transação já gravou.
 *
 * Cuidado que este repository resolve sozinho: o Hibernate junta as alterações e só manda o INSERT/UPDATE ao banco
 * no "flush" (normalmente no fim da transação, ou antes de uma consulta JPQL). Uma consulta JDBC NÃO provoca esse
 * flush. Sem ele, por exemplo, a fn_prioridade_clientes não veria o valor de uma OS que acabou de mudar na mesma
 * transação. Por isso todo método chama descarregarJpa() antes de consultar.
 *
 * O SQL abaixo é o mesmo no SQL Server e no H2 dos testes automáticos (lá os objetos são imitados pelo arquivo
 * src/test/resources/etapa3-h2.sql), por isso só usa SQL padrão: select, where, order by, group by.
 */
@Repository
@RequiredArgsConstructor
public class RotinasBancoRepository {

    private final JdbcClient jdbc;
    private final EntityManager entityManager;

    // ------------------------------------------------------------------ functions

    /** dbo.fn_valor_os(@nr_os): soma de valor_total_item dos itens não cancelados da OS (null conta como 0). */
    public BigDecimal valorOs(int numeroOs) {
        descarregarJpa();
        return jdbc.sql("select dbo.fn_valor_os(:nrOs)")
                .param("nrOs", numeroOs)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * dbo.fn_itens_atrasados(@data_referencia): peças com prazo antes da data de referência e status diferente de
     * 'Executada' e 'Cancelada' (RN-ITE-22). Ordem: mais dias de atraso primeiro.
     */
    public List<ItemAtrasadoResponse> itensAtrasados(LocalDate referencia) {
        descarregarJpa();
        return jdbc.sql("""
                select item_codigo, nr_os, cliente_nome, item_descricao, qtd_total, unidade, prazo_entrega,
                       dias_atraso, status_atual, etapa_pendente, operador_etapa
                from dbo.fn_itens_atrasados(:referencia)
                order by dias_atraso desc, prazo_entrega, item_codigo
                """)
                .param("referencia", Date.valueOf(referencia))
                .query((rs, n) -> new ItemAtrasadoResponse(rs.getInt("item_codigo"), rs.getInt("nr_os"),
                        rs.getString("cliente_nome"), rs.getString("item_descricao"), rs.getBigDecimal("qtd_total"),
                        rs.getString("unidade"), data(rs, "prazo_entrega"), rs.getInt("dias_atraso"),
                        rs.getString("status_atual"), rs.getString("etapa_pendente"), rs.getString("operador_etapa")))
                .list();
    }

    /**
     * dbo.fn_prioridade_clientes(@prioridade_minima, @prioridade_maxima): código do cliente -> prioridade, para
     * todos os clientes (RN-CLI-09). Os limites vêm do application.properties (fenix.prioridade.*).
     */
    public Map<Integer, Integer> prioridadesClientes(int minima, int maxima) {
        descarregarJpa();
        Map<Integer, Integer> prioridades = new LinkedHashMap<>();
        jdbc.sql("select cliente_codigo, prioridade from dbo.fn_prioridade_clientes(:minima, :maxima)")
                .param("minima", minima)
                .param("maxima", maxima)
                .query(rs -> {
                    prioridades.put(rs.getInt("cliente_codigo"), rs.getInt("prioridade"));
                });
        return prioridades;
    }

    /** Prioridade de um cliente só (a mesma função, filtrada pelo cliente). */
    public Optional<Integer> prioridadeCliente(int clienteCodigo, int minima, int maxima) {
        descarregarJpa();
        return jdbc.sql("""
                select prioridade from dbo.fn_prioridade_clientes(:minima, :maxima)
                where cliente_codigo = :cliente
                """)
                .param("minima", minima)
                .param("maxima", maxima)
                .param("cliente", clienteCodigo)
                .query(Integer.class)
                .optional();
    }

    /**
     * dbo.fn_auditoria_timeline_os(@nr_os): eventos da OS em ordem cronológica (status da OS, status dos itens,
     * início e conclusão das etapas). papelResponsavel explica o nome da coluna responsavel (ver
     * EventoAuditoriaResponse): nas etapas é o operador; nos status é quem abriu a OS.
     */
    public List<EventoAuditoriaResponse> linhaDoTempoOs(int numeroOs) {
        descarregarJpa();
        return jdbc.sql("""
                select sequencia, nr_os, data_hora, categoria, item_codigo, referencia, evento, responsavel
                from dbo.fn_auditoria_timeline_os(:nrOs)
                order by sequencia
                """)
                .param("nrOs", numeroOs)
                .query((rs, n) -> {
                    String categoria = rs.getString("categoria");
                    boolean etapa = categoria != null && categoria.startsWith("Etapa");
                    return new EventoAuditoriaResponse(rs.getLong("sequencia"), rs.getInt("nr_os"),
                            dataHora(rs, "data_hora"), categoria, inteiro(rs, "item_codigo"),
                            rs.getString("referencia"), rs.getString("evento"), rs.getString("responsavel"),
                            etapa ? "operador da etapa" : "abriu a OS");
                })
                .list();
    }

    // ------------------------------------------------------------------ views

    private static final String COLUNAS_PAINEL = """
            nr_os, data_entrada, cliente_codigo, cliente_nome, tipo_cliente, documento_cliente, aberta_por, prioridade,
            valor_os, status_atual, status_desde, qtd_itens, qtd_itens_executados, qtd_itens_cancelados,
            qtd_itens_em_aberto, qtd_itens_atrasados, valor_incompleto
            """;

    /** dbo.vw_painel_ordens_servico: todas as OS, da mais nova para a mais antiga. */
    public List<PainelOsResponse> painelOs() {
        descarregarJpa();
        return jdbc.sql("select " + COLUNAS_PAINEL + " from dbo.vw_painel_ordens_servico order by nr_os desc")
                .query((rs, n) -> painel(rs))
                .list();
    }

    /** dbo.vw_painel_ordens_servico de uma OS só. */
    public Optional<PainelOsResponse> painelOs(int numeroOs) {
        descarregarJpa();
        return jdbc.sql("select " + COLUNAS_PAINEL + " from dbo.vw_painel_ordens_servico where nr_os = :nrOs")
                .param("nrOs", numeroOs)
                .query((rs, n) -> painel(rs))
                .optional();
    }

    /** Quantidade de OS por status atual, contada pela view (RN-HOS-03: status atual = último do histórico). */
    public Map<String, Long> osPorStatus() {
        descarregarJpa();
        Map<String, Long> contagem = new LinkedHashMap<>();
        jdbc.sql("""
                select status_atual, count(*) as quantidade
                from dbo.vw_painel_ordens_servico
                group by status_atual
                """)
                .query(rs -> {
                    contagem.put(rs.getString("status_atual"), rs.getLong("quantidade"));
                });
        return contagem;
    }

    /** dbo.vw_produtividade_funcionario entre duas datas de produção (inclusive). */
    public List<ProdutividadeResponse> produtividade(LocalDate inicio, LocalDate fim) {
        descarregarJpa();
        return jdbc.sql("""
                select data_producao, funcionario_codigo, funcionario_nome, cargo, processo, etapas_concluidas,
                       itens_atendidos, etapas_retrabalho, minutos_trabalhados, minutos_medios_por_etapa
                from dbo.vw_produtividade_funcionario
                where data_producao >= :inicio and data_producao <= :fim
                order by data_producao, funcionario_nome, processo
                """)
                .param("inicio", Date.valueOf(inicio))
                .param("fim", Date.valueOf(fim))
                .query((rs, n) -> new ProdutividadeResponse(data(rs, "data_producao"), rs.getInt("funcionario_codigo"),
                        rs.getString("funcionario_nome"), rs.getString("cargo"), rs.getString("processo"),
                        rs.getInt("etapas_concluidas"), rs.getInt("itens_atendidos"), rs.getInt("etapas_retrabalho"),
                        rs.getInt("minutos_trabalhados"), rs.getBigDecimal("minutos_medios_por_etapa")))
                .list();
    }

    /** dbo.vw_retrabalho_mensal a partir do mês de {@code desde} (meses sem item executado não aparecem). */
    public List<RetrabalhoMensalResponse> retrabalhoMensal(LocalDate desde) {
        descarregarJpa();
        return jdbc.sql("""
                select mes_referencia, ano, mes, itens_executados, itens_com_retrabalho, percentual_retrabalho,
                       valor_executado
                from dbo.vw_retrabalho_mensal
                where mes_referencia >= :desde
                order by mes_referencia
                """)
                .param("desde", Date.valueOf(desde.withDayOfMonth(1)))
                .query((rs, n) -> new RetrabalhoMensalResponse(data(rs, "mes_referencia"), rs.getInt("ano"),
                        rs.getInt("mes"), rs.getInt("itens_executados"), rs.getInt("itens_com_retrabalho"),
                        rs.getBigDecimal("percentual_retrabalho"), rs.getBigDecimal("valor_executado")))
                .list();
    }

    // ------------------------------------------------------------------ apoio às procedures

    /**
     * Cronograma de hoje em diante que tem etapa NÃO concluída de itens desta OS: são exatamente as linhas que a
     * dbo.sp_cancelar_ordem_servico apaga de CronogramasItens (mesmo filtro do DELETE da procedure). Lido ANTES de
     * chamar a procedure, para o Java avisar quantas etapas saíram e qual cronograma ficou vazio.
     */
    public List<CronogramaAfetado> cronogramasFuturosComEtapasDaOs(int numeroOs, LocalDate hoje) {
        descarregarJpa();
        return jdbc.sql("""
                select c.codigo, c.data_planejada, count(*) as etapas_da_os,
                       (select count(*) from CronogramasItens t where t.cronograma_codigo = c.codigo) as etapas_no_cronograma
                from CronogramasItens ci
                inner join Cronogramas c on c.codigo = ci.cronograma_codigo
                inner join ItensRotasProcessos irp on irp.codigo = ci.item_rota_processo_codigo
                inner join Itens i on i.codigo = irp.item_codigo
                where i.os_codigo = :nrOs
                  and irp.data_conclusao is null
                  and c.data_planejada >= :hoje
                group by c.codigo, c.data_planejada
                order by c.data_planejada
                """)
                .param("nrOs", numeroOs)
                .param("hoje", Date.valueOf(hoje))
                .query((rs, n) -> new CronogramaAfetado(rs.getInt("codigo"), data(rs, "data_planejada"),
                        rs.getInt("etapas_da_os"), rs.getInt("etapas_no_cronograma")))
                .list();
    }

    /** Cronograma que perde etapas no cancelamento: quantas são da OS cancelada e quantas o cronograma tinha. */
    public record CronogramaAfetado(int codigo, LocalDate dataPlanejada, int etapasDaOs, int etapasNoCronograma) {

        /** Todas as etapas do cronograma eram desta OS: depois do cancelamento ele fica vazio (RN-CRO-01). */
        public boolean ficaVazio() {
            return etapasDaOs >= etapasNoCronograma;
        }
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Manda ao banco o que o Hibernate ainda guarda na memória da transação (flush), para a consulta JDBC enxergar.
     * Só faz sentido dentro de uma transação de escrita: sem transação não há o que descarregar, e na de leitura
     * (readOnly) o Hibernate não acumula alterações.
     */
    void descarregarJpa() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            entityManager.flush();
        }
    }

    private static PainelOsResponse painel(ResultSet rs) throws SQLException {
        return new PainelOsResponse(rs.getInt("nr_os"), dataHora(rs, "data_entrada"), rs.getInt("cliente_codigo"),
                rs.getString("cliente_nome"), rs.getString("tipo_cliente"), rs.getString("documento_cliente"),
                rs.getString("aberta_por"), inteiro(rs, "prioridade"), rs.getBigDecimal("valor_os"),
                rs.getString("status_atual"), dataHora(rs, "status_desde"), rs.getInt("qtd_itens"),
                rs.getInt("qtd_itens_executados"), rs.getInt("qtd_itens_cancelados"), rs.getInt("qtd_itens_em_aberto"),
                rs.getInt("qtd_itens_atrasados"), rs.getBoolean("valor_incompleto"));
    }

    private static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        Date d = rs.getDate(coluna);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime dataHora(ResultSet rs, String coluna) throws SQLException {
        Timestamp t = rs.getTimestamp(coluna);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Integer inteiro(ResultSet rs, String coluna) throws SQLException {
        int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }
}
