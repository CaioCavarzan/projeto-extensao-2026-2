package br.fatec.projetoextensao.enums;

import java.util.List;
import java.util.Locale;

/**
 * Catálogo dos 13 objetos de banco da Etapa 3
 * (script docs/database/objetos_banco.sql, que reúne functions, views, procedures e triggers, nessa ordem).
 *
 * Por que existe: o mesmo nome de objeto aparece em vários lugares (consultas
 * do RotinasBancoRepository, chamadas do
 * ProcedimentosBancoSqlServer, conferência na inicialização, aba Configurações
 * > Banco de dados e tradução de erro
 * "objeto não encontrado"). Com o catálogo num lugar só, a lista é escrita uma
 * vez e cada objeto carrega também:
 * <ul>
 *   <li>o tipo na tabela sys.objects do SQL Server (FN = função escalar, IF = função de tabela inline,
 *       TF = função de tabela com várias instruções, V = view, P = procedure, TR = trigger), usado para conferir se o
 *       objeto está instalado com o tipo certo;</li>
 *   <li>o script do grupo que cria o objeto (dentro do objetos_banco.sql a ordem importa: views, procedures e
 *       triggers usam dbo.fn_valor_os, então as functions vêm primeiro);</li>
 *   <li>onde o sistema usa o objeto, mostrado na tela para o grupo achar o código Java correspondente.</li>
 * </ul>
 */

public enum ObjetoBancoEnum {

    FN_VALOR_OS("dbo.fn_valor_os", "FN", "Função escalar", Scripts.OBJETOS_BANCO,
            "Valor líquido da OS: soma dos itens não cancelados.",
            "ValorOsService.sincronizar (conferência do valor gravado pelos triggers)"),
    FN_ITENS_ATRASADOS("dbo.fn_itens_atrasados", "IF", "Função de tabela (inline)", Scripts.OBJETOS_BANCO,
            "Peças com prazo vencido e não encerradas, com dias de atraso, próxima etapa e operador.",
            "ItemService.listarAtrasados, DashboardService.resumo, sino de alertas e relatório Itens atrasados"),
    FN_PRIORIDADE_CLIENTES("dbo.fn_prioridade_clientes", "TF", "Função de tabela (várias instruções)", Scripts.OBJETOS_BANCO,
            "Prioridade de cada cliente (0 a 100) proporcional ao faturamento das OS não canceladas.",
            "PrioridadeClienteService (tela Clientes e prioridade inicial da OS)"),
    FN_AUDITORIA_TIMELINE_OS("dbo.fn_auditoria_timeline_os", "IF", "Função de tabela (inline)", Scripts.OBJETOS_BANCO,
            "Linha do tempo da OS: status da OS, status dos itens e início/conclusão das etapas.",
            "OrdemServicoService.auditoria (aba Auditoria da tela da OS)"),
    VW_PAINEL_ORDENS_SERVICO("dbo.vw_painel_ordens_servico", "V", "View", Scripts.OBJETOS_BANCO,
            "Painel das OS: cliente, status atual e contagem dos itens (executados, cancelados, atrasados...).",
            "OrdemServicoService.painel/valor incompleto, DashboardService, listas de OS, Clientes e Relatórios"),
    VW_PRODUTIVIDADE_FUNCIONARIO("dbo.vw_produtividade_funcionario", "V", "View", Scripts.OBJETOS_BANCO,
            "Etapas concluídas por dia, funcionário e processo, com retrabalho e minutos trabalhados.",
            "DashboardService (gráficos de produção), IndicadorService (aba Produtividade e relatórios)"),
    VW_RETRABALHO_MENSAL("dbo.vw_retrabalho_mensal", "V", "View", Scripts.OBJETOS_BANCO,
            "Indicador mensal do PDCA: itens executados, itens com retrabalho e % de retrabalho.",
            "DashboardService (gráfico e indicador do mês), relatório Retrabalho mensal"),
    SP_AJUSTAR_ESTOQUE_TINTA("dbo.sp_ajustar_estoque_tinta", "P", "Procedure", Scripts.OBJETOS_BANCO,
            "Entrada ou baixa de estoque da tinta, com a linha travada e saldo nunca negativo.",
            "TintaService.ajustarEstoque (tela Tintas)"),
    SP_CANCELAR_ORDEM_SERVICO("dbo.sp_cancelar_ordem_servico", "P", "Procedure", Scripts.OBJETOS_BANCO,
            "Cancelamento da OS em cascata: itens abertos, cronogramas futuros, status e valor.",
            "OrdemServicoService.cancelar (botão Cancelar OS)"),
    SP_REPLANEJAR_CRONOGRAMA("dbo.sp_replanejar_cronograma", "P", "Procedure", Scripts.OBJETOS_BANCO,
            "Copia as etapas não concluídas de um dia para o cronograma de outro dia.",
            "CronogramaService.replanejar (botão Replanejar pendências)"),
    TRG_ITENS_SINCRONIZAR_VALOR_OS("dbo.trg_itens_sincronizar_valor_os", "TR", "Trigger", Scripts.OBJETOS_BANCO,
            "Confere a cor do item com a cor da tinta e recalcula valor_os a cada mudança em Itens.",
            "Automático ao gravar item (ItemService); o Java só relê o valor (ValorOsService)"),
    TRG_HISTORICOS_STATUS_OS_PROTEGER("dbo.trg_historicos_status_os_proteger", "TR", "Trigger", Scripts.OBJETOS_BANCO,
            "Histórico de status da OS só recebe inclusões; OS encerrada não recebe novo status.",
            "Automático ao gravar status da OS (StatusService)"),
    TRG_HISTORICOS_STATUS_ITENS_SINCRONIZAR_ITEM_E_OS("dbo.trg_historicos_status_itens_sincronizar_item_e_os", "TR",
            "Trigger", Scripts.OBJETOS_BANCO,
            "Histórico do item só recebe inclusões; preenche data_finalizacao e recalcula valor_os.",
            "Automático ao gravar status do item (StatusService)");

    /**
     * Nomes dos scripts do grupo (copiados para o classpath em bancodados/).
     * ALTERADO (20/11/2026): functions.sql, views.sql, procedures.sql e triggers.sql foram unidos em
     * objetos_banco.sql, que já traz os 13 objetos na ordem functions -> views -> procedures -> triggers.
     */
    public static final class Scripts {
        public static final String OBJETOS_BANCO = "objetos_banco.sql";
        public static final String BACKUP_RESTORE = "backup_restore.sql";

        /** Scripts de instalação, na ordem em que rodam (hoje um só: a ordem dos objetos fica dentro do arquivo). */
        public static final List<String> ETAPA3_EM_ORDEM = List.of(OBJETOS_BANCO);

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

    public String getNome() {
        return nome;
    }

    public String getNomeSemSchema() {
        return nome.substring(nome.indexOf('.') + 1);
    }

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
