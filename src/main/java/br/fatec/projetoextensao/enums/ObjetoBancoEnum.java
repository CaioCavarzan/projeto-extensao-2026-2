package br.fatec.projetoextensao.enums;

import java.util.List;
import java.util.Locale;

/**
 * INCLUÍDO (18/11/2026) [E3-02]: catálogo dos 13 objetos de banco da Etapa 3 que o Java passou a acionar
 * (scripts docs/database/functions.sql, views.sql, procedures.sql e triggers.sql).
 *
 * Por que existe: o mesmo nome de objeto aparece em vários lugares (consultas do RotinasBancoRepository, chamadas do
 * ProcedimentosBancoSqlServer, conferência na inicialização, aba Configurações > Banco de dados e tradução de erro
 * "objeto não encontrado"). Com o catálogo num lugar só, a lista é escrita uma vez e cada objeto carrega também:
 * <ul>
 *   <li>o tipo na tabela sys.objects do SQL Server (FN = função escalar, IF = função de tabela inline,
 *       TF = função de tabela com várias instruções, V = view, P = procedure, TR = trigger), usado para conferir se o
 *       objeto está instalado com o tipo certo;</li>
 *   <li>o script do grupo que cria o objeto (a ordem dos scripts importa: views, procedures e triggers usam
 *       dbo.fn_valor_os, então o script de functions vem primeiro);</li>
 *   <li>onde o sistema usa o objeto, mostrado na tela para o grupo achar o código Java correspondente.</li>
 * </ul>
 */
public enum ObjetoBancoEnum {

    FN_VALOR_OS("dbo.fn_valor_os", "FN", "Função escalar", Scripts.FUNCTIONS,
            "Valor líquido da OS: soma dos itens não cancelados.",
            "ValorOsService.sincronizar (conferência do valor gravado pelos triggers)"),
    FN_ITENS_ATRASADOS("dbo.fn_itens_atrasados", "IF", "Função de tabela (inline)", Scripts.FUNCTIONS,
            "Peças com prazo vencido e não encerradas, com dias de atraso, próxima etapa e operador.",
            "ItemService.listarAtrasados, DashboardService.resumo, sino de alertas e relatório Itens atrasados"),
    FN_PRIORIDADE_CLIENTES("dbo.fn_prioridade_clientes", "TF", "Função de tabela (várias instruções)", Scripts.FUNCTIONS,
            "Prioridade de cada cliente (0 a 100) proporcional ao faturamento das OS não canceladas.",
            "PrioridadeClienteService (tela Clientes e prioridade inicial da OS)"),
    FN_AUDITORIA_TIMELINE_OS("dbo.fn_auditoria_timeline_os", "IF", "Função de tabela (inline)", Scripts.FUNCTIONS,
            "Linha do tempo da OS: status da OS, status dos itens e início/conclusão das etapas.",
            "OrdemServicoService.auditoria (aba Auditoria da tela da OS)"),
    VW_PAINEL_ORDENS_SERVICO("dbo.vw_painel_ordens_servico", "V", "View", Scripts.VIEWS,
            "Painel das OS: cliente, status atual e contagem dos itens (executados, cancelados, atrasados...).",
            "OrdemServicoService.painel/valor incompleto, DashboardService, listas de OS, Clientes e Relatórios"),
    VW_PRODUTIVIDADE_FUNCIONARIO("dbo.vw_produtividade_funcionario", "V", "View", Scripts.VIEWS,
            "Etapas concluídas por dia, funcionário e processo, com retrabalho e minutos trabalhados.",
            "DashboardService (gráficos de produção), IndicadorService (aba Produtividade e relatórios)"),
    VW_RETRABALHO_MENSAL("dbo.vw_retrabalho_mensal", "V", "View", Scripts.VIEWS,
            "Indicador mensal do PDCA: itens executados, itens com retrabalho e % de retrabalho.",
            "DashboardService (gráfico e indicador do mês), relatório Retrabalho mensal"),
    SP_AJUSTAR_ESTOQUE_TINTA("dbo.sp_ajustar_estoque_tinta", "P", "Procedure", Scripts.PROCEDURES,
            "Entrada ou baixa de estoque da tinta, com a linha travada e saldo nunca negativo.",
            "TintaService.ajustarEstoque (tela Tintas)"),
    SP_CANCELAR_ORDEM_SERVICO("dbo.sp_cancelar_ordem_servico", "P", "Procedure", Scripts.PROCEDURES,
            "Cancelamento da OS em cascata: itens abertos, cronogramas futuros, status e valor.",
            "OrdemServicoService.cancelar (botão Cancelar OS)"),
    SP_REPLANEJAR_CRONOGRAMA("dbo.sp_replanejar_cronograma", "P", "Procedure", Scripts.PROCEDURES,
            "Copia as etapas não concluídas de um dia para o cronograma de outro dia.",
            "CronogramaService.replanejar (botão Replanejar pendências)"),
    TRG_ITENS_SINCRONIZAR_VALOR_OS("dbo.trg_itens_sincronizar_valor_os", "TR", "Trigger", Scripts.TRIGGERS,
            "Confere a cor do item com a cor da tinta e recalcula valor_os a cada mudança em Itens.",
            "Automático ao gravar item (ItemService); o Java só relê o valor (ValorOsService)"),
    TRG_HISTORICOS_STATUS_OS_PROTEGER("dbo.trg_historicos_status_os_proteger", "TR", "Trigger", Scripts.TRIGGERS,
            "Histórico de status da OS só recebe inclusões; OS encerrada não recebe novo status.",
            "Automático ao gravar status da OS (StatusService)"),
    TRG_HISTORICOS_STATUS_ITENS_SINCRONIZAR_ITEM_E_OS("dbo.trg_historicos_status_itens_sincronizar_item_e_os", "TR",
            "Trigger", Scripts.TRIGGERS,
            "Histórico do item só recebe inclusões; preenche data_finalizacao e recalcula valor_os.",
            "Automático ao gravar status do item (StatusService)");

    /**
     * Nomes dos scripts do grupo, na ordem em que precisam rodar (copiados para o classpath em bancodados/).
     * ALTERADO (19/11/2026): nomes curtos (antes etapa3_01_functions.sql a etapa3_04_triggers.sql). Como o nome
     * do arquivo não diz mais a ordem, ela vale só por ETAPA3_EM_ORDEM.
     */
    public static final class Scripts {
        public static final String FUNCTIONS = "functions.sql";
        public static final String VIEWS = "views.sql";
        public static final String PROCEDURES = "procedures.sql";
        public static final String TRIGGERS = "triggers.sql";
        public static final String BACKUP_RESTORE = "backup_restore.sql";

        /** Ordem de instalação: functions -> views -> procedures -> triggers (dependências do fn_valor_os). */
        public static final List<String> ETAPA3_EM_ORDEM = List.of(FUNCTIONS, VIEWS, PROCEDURES, TRIGGERS);

        private Scripts() {
        }
    }

    private final String nome;
    private final String tipoSqlServer;
    private final String tipoDescricao;
    private final String script;
    private final String finalidade;
    private final String usoNoSistema;

    ObjetoBancoEnum(String nome, String tipoSqlServer, String tipoDescricao, String script, String finalidade,
                    String usoNoSistema) {
        this.nome = nome;
        this.tipoSqlServer = tipoSqlServer;
        this.tipoDescricao = tipoDescricao;
        this.script = script;
        this.finalidade = finalidade;
        this.usoNoSistema = usoNoSistema;
    }

    /** Nome com o schema, como está nos scripts (ex.: dbo.fn_valor_os). */
    public String getNome() {
        return nome;
    }

    /** Nome sem o schema (ex.: fn_valor_os), como fica na coluna sys.objects.name. */
    public String getNomeSemSchema() {
        return nome.substring(nome.indexOf('.') + 1);
    }

    /** Código do tipo em sys.objects.type (FN, IF, TF, V, P, TR). */
    public String getTipoSqlServer() {
        return tipoSqlServer;
    }

    public String getTipoDescricao() {
        return tipoDescricao;
    }

    public String getScript() {
        return script;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public String getUsoNoSistema() {
        return usoNoSistema;
    }

    /**
     * Objeto do catálogo citado num texto (ex.: a mensagem "Invalid object name 'dbo.fn_itens_atrasados'" do SQL
     * Server). Procura pelo nome sem o schema, que aparece em qualquer idioma da mensagem; null se nenhum aparece.
     */
    public static ObjetoBancoEnum citadoEm(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.toLowerCase(Locale.ROOT);
        for (ObjetoBancoEnum o : values()) {
            if (t.contains(o.getNomeSemSchema().toLowerCase(Locale.ROOT))) {
                return o;
            }
        }
        return null;
    }
}
