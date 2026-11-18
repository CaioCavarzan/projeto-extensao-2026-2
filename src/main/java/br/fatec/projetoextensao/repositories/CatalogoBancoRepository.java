package br.fatec.projetoextensao.repositories;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.dtos.BackupResponse;
import br.fatec.projetoextensao.dtos.ObjetoBancoResponse;
import br.fatec.projetoextensao.enums.ObjetoBancoEnum;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (18/11/2026) [E3-16]: consultas ao catálogo do próprio SQL Server, para saber se os 13 objetos da Etapa 3
 * estão instalados (sys.objects e sys.triggers) e qual foi o último backup (msdb). Usado pela aba Configurações >
 * Banco de dados (BancoDadosService) e pela conferência ao iniciar o sistema (VerificacaoObjetosBanco).
 *
 * Só funciona no SQL Server (as tabelas sys.* e o banco msdb não existem no H2 dos testes); por isso quem usa
 * pergunta antes ehSqlServer().
 */
@Repository
@RequiredArgsConstructor
public class CatalogoBancoRepository {

    private static final Logger log = LoggerFactory.getLogger(CatalogoBancoRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    /** O banco configurado em spring.datasource.url é SQL Server? (H2 nos testes automáticos) */
    public boolean ehSqlServer() {
        Boolean sim = jdbcTemplate.execute((ConnectionCallback<Boolean>) c ->
                c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sql server"));
        return Boolean.TRUE.equals(sim);
    }

    /** Nome do banco em que a conexão do sistema está (db_name()). */
    public String bancoAtual() {
        return jdbc.sql("select db_name()").query(String.class).single();
    }

    /** Primeira linha de @@version (ex.: "Microsoft SQL Server 2022 (RTM) - 16.0.1000.6 (X64)"). */
    public String versao() {
        String v = jdbc.sql("select @@version").query(String.class).single();
        return v == null ? null : v.lines().findFirst().orElse(v).trim();
    }

    /**
     * Situação de cada objeto do catálogo (ObjetoBancoEnum): existe em sys.objects no schema dbo? com o tipo
     * esperado? (trigger) está habilitado? quando foi alterado pela última vez?
     */
    public List<ObjetoBancoResponse> situacaoObjetos() {
        List<String> nomes = new ArrayList<>();
        for (ObjetoBancoEnum o : ObjetoBancoEnum.values()) {
            nomes.add(o.getNomeSemSchema());
        }
        Map<String, Object[]> encontrados = new HashMap<>();
        jdbc.sql("""
                select o.name as nome, o.type as tipo, o.modify_date as alterado_em, t.is_disabled as desabilitado
                from sys.objects as o
                left join sys.triggers as t on t.object_id = o.object_id
                where o.schema_id = schema_id('dbo') and o.name in (:nomes)
                """)
                .param("nomes", nomes)
                .query(rs -> {
                    Timestamp alterado = rs.getTimestamp("alterado_em");
                    boolean desabilitado = rs.getBoolean("desabilitado");
                    boolean semTrigger = rs.wasNull();
                    encontrados.put(rs.getString("nome").toLowerCase(Locale.ROOT), new Object[] {
                        rs.getString("tipo").trim(), alterado == null ? null : alterado.toLocalDateTime(),
                        semTrigger ? null : !desabilitado});
                });
        List<ObjetoBancoResponse> lista = new ArrayList<>();
        for (ObjetoBancoEnum o : ObjetoBancoEnum.values()) {
            Object[] linha = encontrados.get(o.getNomeSemSchema().toLowerCase(Locale.ROOT));
            boolean instalado = linha != null;
            boolean tipoConfere = instalado && o.getTipoSqlServer().equals(linha[0]);
            lista.add(new ObjetoBancoResponse(o.getNome(), o.getTipoDescricao(), o.getScript(), o.getFinalidade(),
                    o.getUsoNoSistema(), instalado, tipoConfere, instalado ? (Boolean) linha[2] : null,
                    instalado ? (LocalDateTime) linha[1] : null));
        }
        return lista;
    }

    /**
     * Último backup completo (type = 'D') do banco, pelo histórico que o próprio SQL Server grava em msdb. Vazio se
     * nunca houve backup ou se o login do sistema não pode ler o msdb (o motivo vai para o log).
     */
    public Optional<BackupResponse> ultimoBackup(String banco) {
        try {
            return jdbc.sql("""
                    select top (1) bs.backup_finish_date, bs.backup_size, bmf.physical_device_name
                    from msdb.dbo.backupset as bs
                    inner join msdb.dbo.backupmediafamily as bmf on bmf.media_set_id = bs.media_set_id
                    where bs.database_name = :banco and bs.type = 'D'
                    order by bs.backup_finish_date desc
                    """)
                    .param("banco", banco)
                    .query((rs, n) -> {
                        Timestamp fim = rs.getTimestamp("backup_finish_date");
                        BigDecimal tamanho = rs.getBigDecimal("backup_size");
                        return new BackupResponse(banco, rs.getString("physical_device_name"),
                                fim == null ? null : fim.toLocalDateTime(), tamanho == null ? null : tamanho.longValue(),
                                List.of());
                    })
                    .optional();
        } catch (DataAccessException e) {
            log.warn("Não foi possível ler o histórico de backup em msdb: {}", e.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
    }
}
