package br.fatec.projetoextensao.exceptions;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.fatec.projetoextensao.enums.ObjetoBancoEnum;

/**
 * Tradução dos erros que os objetos da Etapa 3 levantam no SQL Server para as exceções
 * de negócio do projeto (RegraNegocioException, RecursoNaoEncontradoException e ObjetoBancoAusenteException).
 *
 * Por que existe: as procedures e os triggers do grupo validam regras e, quando uma regra falha, fazem
 * "THROW 5xxxx, 'mensagem', 1". No Java esse erro chega como SQLException (código = 5xxxx) embrulhada em
 * exceções do Spring ou do Hibernate (DataAccessException, PersistenceException...), em profundidades diferentes
 * conforme o caminho (JdbcTemplate, flush do Hibernate, commit). Sem esta classe, a mensagem em português escrita
 * pelo grupo no THROW se perderia e a tela mostraria "erro interno" (HTTP 500).
 *
 * Como funciona: percorre a cadeia de causas (getCause) e, em cada SQLException, a lista de erros seguintes
 * (getNextException: o SQL Server pode mandar mais de um erro na mesma resposta, por exemplo o 50003 seguido do
 * 266 "Transaction count after EXECUTE..." quando a procedure faz ROLLBACK). O primeiro código conhecido decide:
 * <pre>
 *  código  origem                                     resultado no Java              HTTP
 *  50001   sp_ajustar_estoque_tinta (quantidade 0)    RegraNegocioException          400
 *  50002   sp_ajustar_estoque_tinta (tinta)           RecursoNaoEncontradoException  404
 *  50003   sp_ajustar_estoque_tinta (sem saldo)       RegraNegocioException          400
 *  50010   sp_cancelar_ordem_servico (OS)             RecursoNaoEncontradoException  404
 *  50011   sp_cancelar_ordem_servico (concluída)      RegraNegocioException          400
 *  50012   sp_cancelar_ordem_servico (já cancelada)   RegraNegocioException          400
 *  50020   sp_replanejar_cronograma (datas)           RegraNegocioException          400
 *  50021   sp_replanejar_cronograma (usuário)         RegraNegocioException          400
 *  50101   trg_historicos_status_os_proteger          RegraNegocioException          409
 *  50102   trg_historicos_status_os_proteger          RegraNegocioException          409
 *  50110   trg_itens_sincronizar_valor_os             RegraNegocioException          400
 *  50120   trg_historicos_status_itens_sincronizar... RegraNegocioException          409
 *  8115    estouro aritmético (decimal)               RegraNegocioException          400
 *  208, 2812, 4121  objeto da Etapa 3 não instalado   ObjetoBancoAusenteException    503
 * </pre>
 * Para os erros 5xxxx a mensagem mostrada é a do próprio THROW do script (o texto que o grupo escreveu), para a
 * tela e o banco falarem a mesma língua. 208/2812/4121 só são traduzidos quando a mensagem cita um dos 13 objetos
 * do catálogo (ObjetoBancoEnum); outra tabela ou objeto inexistente continua sendo erro interno.
 */

public final class ErrosBanco {

    // Erros lançados pelos scripts da Etapa 3 (THROW). Os números são os dos scripts do grupo.
    public static final int AJUSTE_QUANTIDADE_ZERO = 50001;
    public static final int AJUSTE_TINTA_NAO_ENCONTRADA = 50002;
    public static final int AJUSTE_ESTOQUE_INSUFICIENTE = 50003;
    public static final int CANCELAMENTO_OS_NAO_ENCONTRADA = 50010;
    public static final int CANCELAMENTO_OS_CONCLUIDA = 50011;
    public static final int CANCELAMENTO_OS_JA_CANCELADA = 50012;
    public static final int REPLANEJAMENTO_DATAS_INVALIDAS = 50020;
    public static final int REPLANEJAMENTO_USUARIO_INVALIDO = 50021;
    public static final int HISTORICO_OS_IMUTAVEL = 50101;
    public static final int OS_ENCERRADA_NAO_MUDA_STATUS = 50102;
    public static final int COR_DO_ITEM_DIVERGE_DA_TINTA = 50110;
    public static final int HISTORICO_ITEM_IMUTAVEL = 50120;

    // Erros do próprio SQL Server que interessam à integração.
    public static final int ESTOURO_ARITMETICO = 8115;
    public static final int OBJETO_INEXISTENTE = 208;
    public static final int PROCEDURE_INEXISTENTE = 2812;
    public static final int FUNCAO_INEXISTENTE = 4121;

    /** Erro do banco identificado: o código e a mensagem original (para os 5xxxx, o texto do THROW). */
    public record ErroBanco(int codigo, String mensagem) {
    }

    private enum Destino { INVALIDO, CONFLITO, NAO_ENCONTRADO, OBJETO_AUSENTE }

    /** mensagemFixa = null: usa a mensagem que veio do banco. */
    private record Traducao(Destino destino, String mensagemFixa) {
    }

    private static final Map<Integer, Traducao> TRADUCOES = Map.ofEntries(
            Map.entry(AJUSTE_QUANTIDADE_ZERO, new Traducao(Destino.INVALIDO, null)),
            Map.entry(AJUSTE_TINTA_NAO_ENCONTRADA, new Traducao(Destino.NAO_ENCONTRADO, null)),
            Map.entry(AJUSTE_ESTOQUE_INSUFICIENTE, new Traducao(Destino.INVALIDO, null)),
            Map.entry(CANCELAMENTO_OS_NAO_ENCONTRADA, new Traducao(Destino.NAO_ENCONTRADO, null)),
            Map.entry(CANCELAMENTO_OS_CONCLUIDA, new Traducao(Destino.INVALIDO, null)),
            Map.entry(CANCELAMENTO_OS_JA_CANCELADA, new Traducao(Destino.INVALIDO, null)),
            Map.entry(REPLANEJAMENTO_DATAS_INVALIDAS, new Traducao(Destino.INVALIDO, null)),
            Map.entry(REPLANEJAMENTO_USUARIO_INVALIDO, new Traducao(Destino.INVALIDO, null)),
            Map.entry(HISTORICO_OS_IMUTAVEL, new Traducao(Destino.CONFLITO, null)),
            Map.entry(OS_ENCERRADA_NAO_MUDA_STATUS, new Traducao(Destino.CONFLITO, null)),
            Map.entry(COR_DO_ITEM_DIVERGE_DA_TINTA, new Traducao(Destino.INVALIDO, null)),
            Map.entry(HISTORICO_ITEM_IMUTAVEL, new Traducao(Destino.CONFLITO, null)),
            Map.entry(ESTOURO_ARITMETICO, new Traducao(Destino.INVALIDO,
                    "Valor acima do limite que a coluna do banco aceita.")),
            Map.entry(OBJETO_INEXISTENTE, new Traducao(Destino.OBJETO_AUSENTE, null)),
            Map.entry(PROCEDURE_INEXISTENTE, new Traducao(Destino.OBJETO_AUSENTE, null)),
            Map.entry(FUNCAO_INEXISTENTE, new Traducao(Destino.OBJETO_AUSENTE, null)));

    private ErrosBanco() {
    }

    /**
     * Primeiro erro de código conhecido na cadeia da exceção. A mensagem é a do SQLException mais interno com o mesmo
     * código: no SQL Server há um só; no H2 dos testes, a exceção de fora repete o código mas acrescenta o comando
     * SQL ao texto, e a de dentro tem só o texto original.
     */
    public static Optional<ErroBanco> identificar(Throwable ex) {
        List<SQLException> erros = sqlExceptions(ex);
        for (SQLException e : erros) {
            if (TRADUCOES.containsKey(e.getErrorCode()) && relevante(e)) {
                SQLException maisInterno = e;
                for (SQLException outro : erros) {
                    if (outro.getErrorCode() == e.getErrorCode()) {
                        maisInterno = outro;
                    }
                }
                return Optional.of(new ErroBanco(e.getErrorCode(), limpar(maisInterno.getMessage())));
            }
        }
        return Optional.empty();
    }

    /** O erro da cadeia tem este código? (usado nos testes e em decisões pontuais dos Services) */
    public static boolean temCodigo(Throwable ex, int codigo) {
        return sqlExceptions(ex).stream().anyMatch(e -> e.getErrorCode() == codigo);
    }

    /**
     * Exceção de negócio equivalente ao erro do banco, ou null se a cadeia não tiver erro conhecido (quem chamou
     * decide: normalmente relança a exceção original, que vira HTTP 500 com a mensagem genérica).
     */
    public static RuntimeException traduzir(Throwable ex) {
        Optional<ErroBanco> erro = identificar(ex);
        if (erro.isEmpty()) {
            return null;
        }
        Traducao t = TRADUCOES.get(erro.get().codigo());
        String mensagem = t.mensagemFixa() != null ? t.mensagemFixa() : erro.get().mensagem();
        return switch (t.destino()) {
            case INVALIDO -> new RegraNegocioException(mensagem);
            case CONFLITO -> RegraNegocioException.conflito(mensagem);
            case NAO_ENCONTRADO -> new RecursoNaoEncontradoException(mensagem);
            case OBJETO_AUSENTE -> new ObjetoBancoAusenteException(ObjetoBancoEnum.citadoEm(erro.get().mensagem()));
        };
    }

    /** Atalho dos Services: devolve a exceção traduzida ou, se não houver tradução, a própria exceção recebida. */
    public static RuntimeException converter(RuntimeException ex) {
        RuntimeException traduzida = traduzir(ex);
        return traduzida != null ? traduzida : ex;
    }

    // ------------------------------------------------------------------ apoio

    /** 208/2812/4121 só contam quando citam um objeto da Etapa 3; os demais códigos contam sempre. */
    private static boolean relevante(SQLException e) {
        Traducao t = TRADUCOES.get(e.getErrorCode());
        return t.destino() != Destino.OBJETO_AUSENTE || ObjetoBancoEnum.citadoEm(e.getMessage()) != null;
    }

    /** Todas as SQLException da cadeia, na ordem: causas (getCause) e, em cada uma, os erros seguintes (getNextException). */
    private static List<SQLException> sqlExceptions(Throwable ex) {
        List<SQLException> lista = new ArrayList<>();
        Set<Throwable> causas = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<SQLException> incluidas = Collections.newSetFromMap(new IdentityHashMap<>());
        // causas.add evita laço infinito se alguma biblioteca montar uma cadeia circular de causas
        for (Throwable t = ex; t != null && causas.add(t); t = t.getCause()) {
            if (t instanceof SQLException sql) {
                for (SQLException s = sql; s != null && incluidas.add(s); s = s.getNextException()) {
                    lista.add(s);
                }
            }
        }
        return lista;
    }

    /** Tira o que o driver acrescenta depois da mensagem (no H2: "; SQL statement: ..." e o código do erro). */
    private static String limpar(String mensagem) {
        if (mensagem == null) {
            return "Não foi possível concluir: o banco recusou a operação.";
        }
        String m = mensagem;
        int corte = m.indexOf("; SQL statement");
        if (corte >= 0) {
            m = m.substring(0, corte);
        }
        int linha = m.indexOf('\n');
        if (linha >= 0) {
            m = m.substring(0, linha);
        }
        return m.trim();
    }
}
