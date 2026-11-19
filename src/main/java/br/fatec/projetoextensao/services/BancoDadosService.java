package br.fatec.projetoextensao.services;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import br.fatec.projetoextensao.dtos.BackupResponse;
import br.fatec.projetoextensao.dtos.ObjetoBancoResponse;
import br.fatec.projetoextensao.dtos.OperacaoBancoResponse;
import br.fatec.projetoextensao.dtos.RestauracaoRequest;
import br.fatec.projetoextensao.dtos.SituacaoBancoResponse;
import br.fatec.projetoextensao.enums.ObjetoBancoEnum;
import br.fatec.projetoextensao.enums.ObjetoBancoEnum.Scripts;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.repositories.CatalogoBancoRepository;
import br.fatec.projetoextensao.security.Perfis;
import lombok.RequiredArgsConstructor;

/**
 * Aba Configurações > Banco de dados: situação dos 13 objetos
 * da Etapa 3, instalação dos scripts, backup e restore com o script backup_restore.sql do grupo.
 */

@Service
@RequiredArgsConstructor
public class BancoDadosService {

    private static final Logger log = LoggerFactory.getLogger(BancoDadosService.class);

    public static final String BANCO = "banco_fenix";
    public static final String CONFIRMACAO_RESTORE = "RESTAURAR " + BANCO;

    private static final String MARCA_BACKUP = "backup database";
    private static final String MARCA_RESTORE = "restore database";

    private final DataSource dataSource;
    private final DataSourceProperties propriedades;
    private final CatalogoBancoRepository catalogo;
    private final UsuarioLogado usuarioLogado;

    @PreAuthorize(Perfis.ADMIN)
    public SituacaoBancoResponse situacao() {
        exigirSqlServer();
        return montarSituacao();
    }

    @PreAuthorize(Perfis.ADMIN)
    public OperacaoBancoResponse instalarObjetos() {
        exigirBancoDoSistema();
        List<String> mensagens = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(true);
            for (String script : Scripts.ETAPA3_EM_ORDEM) {
                int executados = 0;
                for (String lote : ScriptsBanco.lotes(ScriptsBanco.ler(script))) {
                    if (ScriptsBanco.ehUse(lote)) {
                        continue;
                    }
                    executarLote(c, lote, mensagens);
                    executados++;
                }
                mensagens.add(script + ": " + executados + " objeto(s) criado(s) ou atualizado(s).");
            }
        } catch (SQLException e) {
            throw falha("instalar os objetos da Etapa 3", e);
        }
        log.info("AUDITORIA login={} acao=INSTALAR_OBJETOS_ETAPA3", usuarioLogado.login().orElse("-"));
        return new OperacaoBancoResponse("Instalação dos objetos da Etapa 3", LocalDateTime.now(), mensagens,
                montarSituacao());
    }

    @PreAuthorize(Perfis.ADMIN)
    public BackupResponse fazerBackup() {
        exigirBancoDoSistema();
        List<String> mensagens = new ArrayList<>();
        try (Connection c = abrirConexaoNoMaster()) {
            executarRotina(c, MARCA_BACKUP, mensagens);
        } catch (SQLException e) {
            throw falha("fazer o backup", e);
        }
        log.info("AUDITORIA login={} acao=BACKUP_BANCO banco={}", usuarioLogado.login().orElse("-"), BANCO);
        BackupResponse ultimo = catalogo.ultimoBackup(BANCO).orElse(null);
        return new BackupResponse(BANCO, ultimo == null ? null : ultimo.arquivo(),
                ultimo == null ? LocalDateTime.now() : ultimo.concluidoEm(), ultimo == null ? null : ultimo.tamanhoBytes(),
                mensagens);
    }

    @PreAuthorize(Perfis.ADMIN)
    public OperacaoBancoResponse restaurar(RestauracaoRequest dto) {
        String confirmacao = dto == null || dto.confirmacao() == null ? "" : dto.confirmacao().trim();
        if (!CONFIRMACAO_RESTORE.equals(confirmacao)) {
            throw new RegraNegocioException("Para restaurar, digite exatamente: " + CONFIRMACAO_RESTORE);
        }
        exigirBancoDoSistema();
        BackupResponse ultimo = catalogo.ultimoBackup(BANCO).orElseThrow(() -> new RegraNegocioException(
                "Não há backup completo registrado do banco " + BANCO + ": faça um backup antes de restaurar."));
        String login = usuarioLogado.login().orElse("-");
        log.warn("AUDITORIA login={} acao=RESTORE_BANCO banco={} backupDe={}", login, BANCO, ultimo.concluidoEm());
        List<String> mensagens = new ArrayList<>();
        descartarConexoesDoPool();
        Connection c = null;
        try {
            c = abrirConexaoNoMaster();
            executarRotina(c, MARCA_RESTORE, mensagens);
        } catch (SQLException e) {
            throw falha("restaurar o banco", e);
        } finally {
            voltarParaMultiUser(c);
            fechar(c);
            descartarConexoesDoPool();
        }
        log.warn("AUDITORIA login={} acao=RESTORE_BANCO_CONCLUIDO banco={}", login, BANCO);
        return new OperacaoBancoResponse("Restore do banco " + BANCO, LocalDateTime.now(), mensagens, montarSituacao());
    }

    // ------------------------------------------------------------------ apoio

    private SituacaoBancoResponse montarSituacao() {
        List<ObjetoBancoResponse> objetos = catalogo.situacaoObjetos();
        int instalados = (int) objetos.stream().filter(o -> o.instalado() && o.tipoConfere()).count();
        return new SituacaoBancoResponse(catalogo.bancoAtual(), catalogo.versao(), instalados,
                ObjetoBancoEnum.values().length, objetos, catalogo.ultimoBackup(BANCO).orElse(null));
    }

    /** Recursos que só existem no SQL Server (sys.objects, msdb, BACKUP): no H2 dos testes, recusa com mensagem clara. */
    private void exigirSqlServer() {
        if (!catalogo.ehSqlServer()) {
            throw new RegraNegocioException("Disponível só com o SQL Server (o banco atual não é SQL Server).");
        }
    }

    private void exigirBancoDoSistema() {
        exigirSqlServer();
        String atual = catalogo.bancoAtual();
        if (!BANCO.equalsIgnoreCase(atual)) {
            throw new RegraNegocioException("Os scripts da Etapa 3 são do banco " + BANCO + ", mas o sistema está ligado ao "
                    + "banco " + atual + ". Rode os scripts pelo SSMS ou ajuste spring.datasource.url.");
        }
    }

    private void executarRotina(Connection c, String marca, List<String> mensagens) throws SQLException {
        List<String> rotinas = new ArrayList<>();
        for (String lote : ScriptsBanco.lotes(ScriptsBanco.ler(Scripts.BACKUP_RESTORE))) {
            if (ScriptsBanco.ehUse(lote)) {
                executarLote(c, lote, mensagens); // conexão separada: o "use master" do script pode rodar aqui
            } else if (ScriptsBanco.semComentarios(lote).toLowerCase(Locale.ROOT).contains(marca)) {
                rotinas.add(lote);
            }
        }
        if (rotinas.size() != 1) {
            throw new IllegalStateException("O backup_restore.sql deveria ter um lote com '" + marca + "' e tem "
                    + rotinas.size() + ": confira o script.");
        }
        executarLote(c, rotinas.get(0), mensagens);
    }

    private static void executarLote(Connection c, String lote, List<String> mensagens) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.setEscapeProcessing(false);
            boolean temResultado = st.execute(lote);
            while (true) {
                coletarAvisos(st, mensagens);
                if (temResultado) {
                    try (ResultSet rs = st.getResultSet()) {
                        while (rs.next()) {
                        }
                    }
                } else if (st.getUpdateCount() == -1) {
                    break;
                }
                temResultado = st.getMoreResults();
            }
            coletarAvisos(st, mensagens);
        }
    }

    private static void coletarAvisos(Statement st, List<String> mensagens) throws SQLException {
        for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
            if (w.getMessage() != null && !w.getMessage().isBlank()) {
                mensagens.add(w.getMessage().trim());
            }
        }
        st.clearWarnings();
    }

    private Connection abrirConexaoNoMaster() throws SQLException {
        String url = propriedades.determineUrl();
        String urlMaster = url.matches("(?i).*(databaseName|database)=.*")
                ? url.replaceAll("(?i)(databaseName|database)=[^;]*", "databaseName=master")
                : url + (url.endsWith(";") ? "" : ";") + "databaseName=master";
        Properties p = new Properties();
        if (propriedades.determineUsername() != null) {
            p.setProperty("user", propriedades.determineUsername());
        }
        if (propriedades.determinePassword() != null) {
            p.setProperty("password", propriedades.determinePassword());
        }
        try {
            Driver driver = (Driver) Class.forName(propriedades.determineDriverClassName()).getDeclaredConstructor()
                    .newInstance();
            Connection c = driver.connect(urlMaster, p);
            if (c == null) {
                throw new SQLException("O driver não aceitou a URL do banco: " + urlMaster);
            }
            c.setAutoCommit(true); 
            return c;
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Driver do banco não encontrado: " + propriedades.determineDriverClassName(), e);
        }
    }

    private static void voltarParaMultiUser(Connection c) {
        if (c == null) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.execute("if db_id(N'" + BANCO + "') is not null alter database " + BANCO + " set multi_user");
        } catch (SQLException e) {
            log.error("Não foi possível voltar o banco {} para MULTI_USER. Rode no SSMS: alter database {} set multi_user",
                    BANCO, BANCO, e);
        }
    }

    private static void fechar(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException e) {
                log.debug("Conexão do restore já estava fechada: {}", e.getMessage());
            }
        }
    }

    private void descartarConexoesDoPool() {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
                if (pool != null) {
                    pool.softEvictConnections();
                }
            }
        } catch (SQLException e) {
            log.warn("Não foi possível renovar as conexões do pool: {}", e.getMessage());
        }
    }

    private static RegraNegocioException falha(String acao, SQLException e) {
        log.error("Falha ao {} (erro {} do SQL Server)", acao, e.getErrorCode(), e);
        String detalhe = e.getMessage() == null ? "" : " Mensagem do SQL Server: " + e.getMessage();
        return switch (e.getErrorCode()) {
            case 3201 -> new RegraNegocioException("O SQL Server não encontrou o arquivo de backup na pasta padrão da "
                    + "instância: faça um backup antes de restaurar." + detalhe);
            case 3101 -> RegraNegocioException.conflito("O SQL Server não conseguiu uso exclusivo do banco (há conexões "
                    + "abertas). Feche o SSMS e outros programas ligados ao " + BANCO + " e tente de novo." + detalhe);
            case 229, 262, 916, 15151, 15247 -> new RegraNegocioException("O login usado pelo sistema não tem permissão "
                    + "para " + acao + " no SQL Server (é preciso sysadmin, ou db_backupoperator/dbcreator)." + detalhe);
            default -> new RegraNegocioException("Não foi possível " + acao + "." + detalhe);
        };
    }
}
