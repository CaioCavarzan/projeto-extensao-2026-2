package br.fatec.projetoextensao.bancodados;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.repositories.NumeracaoOs;

/**
 * INCLUÍDO (19/11/2026): NumeracaoOs para o H2 dos testes automáticos, que não tem sys.identity_columns. No H2 o
 * IDENTITY é uma sequência, e INFORMATION_SCHEMA.COLUMNS.IDENTITY_BASE é o próximo valor que ela vai entregar
 * (também já contando os números consumidos por ROLLBACK, como no SQL Server).
 */
@Component
@Primary
@Profile("test")
public class NumeracaoOsH2 implements NumeracaoOs {

    private final JdbcTemplate jdbc;

    public NumeracaoOsH2(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int proximoNumero() {
        Long numero = jdbc.queryForObject("""
                select IDENTITY_BASE from INFORMATION_SCHEMA.COLUMNS
                where lower(TABLE_NAME) = 'ordensdeservico' and lower(COLUMN_NAME) = 'nr_os'
                """, Long.class);
        return numero == null ? 1 : numero.intValue();
    }
}
