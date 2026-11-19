package br.fatec.projetoextensao.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (19/11/2026): próximo nr_os lido do IDENTITY de OrdensDeServico no SQL Server.
 *
 * sys.identity_columns.last_value é o último número gerado (inclusive os consumidos por ROLLBACK) e fica NULL
 * enquanto a tabela nunca recebeu linha; nesse caso o próximo é o próprio seed.
 */
@Repository
@RequiredArgsConstructor
public class NumeracaoOsSqlServer implements NumeracaoOs {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int proximoNumero() {
        Long numero = jdbcTemplate.queryForObject("""
                select coalesce(cast(last_value as bigint) + cast(increment_value as bigint), cast(seed_value as bigint))
                from sys.identity_columns
                where object_id = object_id('dbo.OrdensDeServico')
                """, Long.class);
        return numero == null ? 1 : numero.intValue();
    }
}
