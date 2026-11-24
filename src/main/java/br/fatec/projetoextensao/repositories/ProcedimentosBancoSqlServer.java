package br.fatec.projetoextensao.repositories;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;

import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Chamada das 3 stored procedures da Etapa 3 no SQL Server, pelo JDBC.
 */

@Repository
@RequiredArgsConstructor
public class ProcedimentosBancoSqlServer implements ProcedimentosBanco {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Override
    public BigDecimal ajustarEstoqueTinta(int tintaCodigo, BigDecimal quantidade) {
        descarregarJpa();
        return jdbcTemplate.execute("{call dbo.sp_ajustar_estoque_tinta(?, ?, ?)}",
                (CallableStatementCallback<BigDecimal>) (CallableStatement cs) -> {
                    cs.setInt(1, tintaCodigo);                          // @tinta_codigo int
                    cs.setBigDecimal(2, quantidade);                    // @quantidade decimal(10, 2)
                    cs.registerOutParameter(3, Types.DECIMAL, 2);       // @estoque_resultante decimal(10, 2) output
                    cs.execute();
                    return cs.getBigDecimal(3);
                });
    }

    @Override
    public int cancelarOrdemServico(int numeroOs) {
        descarregarJpa();
        Integer cancelados = jdbcTemplate.execute("{call dbo.sp_cancelar_ordem_servico(?, ?)}",
                (CallableStatementCallback<Integer>) (CallableStatement cs) -> {
                    cs.setInt(1, numeroOs);                             // @nr_os int
                    cs.registerOutParameter(2, Types.INTEGER);          // @itens_cancelados int output
                    cs.execute();
                    return cs.getInt(2);
                });
        return cancelados == null ? 0 : cancelados;
    }

    @Override
    public int replanejarCronograma(LocalDate dataOrigem, LocalDate dataDestino, int usuarioCodigo) {
        descarregarJpa();
        Integer movidas = jdbcTemplate.execute("{call dbo.sp_replanejar_cronograma(?, ?, ?, ?)}",
                (CallableStatementCallback<Integer>) (CallableStatement cs) -> {
                    cs.setDate(1, Date.valueOf(dataOrigem));            // @data_origem date
                    cs.setDate(2, Date.valueOf(dataDestino));           // @data_destino date
                    cs.setInt(3, usuarioCodigo);                        // @usuario_codigo int (Usuarios.funcionario_codigo)
                    cs.registerOutParameter(4, Types.INTEGER);          // @etapas_movidas int output
                    cs.execute();
                    return cs.getInt(4);
                });
        return movidas == null ? 0 : movidas;
    }

    private void descarregarJpa() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            entityManager.flush();
        }
    }
}
