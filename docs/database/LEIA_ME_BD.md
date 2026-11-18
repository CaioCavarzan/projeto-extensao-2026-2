# Documentação Técnica e Auditoria Arquitetural — Etapa 3 (`banco_fenix`)

> **Projeto:** Sistema de Gestão Industrial Fênix Metalúrgica (Java 21 LTS + Spring Boot 3 + SQL Server)  
> **Escopo da Entrega:** 13 Artefatos Avançados de Banco de Dados (4 Functions, 3 Views, 3 Stored Procedures e 3 Triggers) + Rotina de Backup e Restore + Auditoria de Integração com a Camada Java.  
> **Padrão de Codificação (Code Style):** Todas as instruções T-SQL, funções nativas, tipos de dados, variáveis e nomes dos novos objetos seguem rigorosamente o padrão **100% minúsculo (`lowercase`)**, preservando exclusivamente a grafia original das tabelas/colunas definidas em [schema.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/schema.sql) e os literais de texto das regras de negócio.

---

## Sumário Executivo e Ordem de Execução dos Scripts

Os artefatos foram projetados sob a diretriz **"Simples, mas de Mercado"**: operações críticas de negócio deixam de ser processadas em loops na memória da aplicação (`N+1 queries`) e passam a ser executadas de forma *set-based* (orientada a conjuntos), atômica e segura diretamente no motor do **SQL Server**.

Como as **Views**, **Procedures** e **Triggers** invocam a função escalar `dbo.fn_valor_os`, execute os scripts na ordem abaixo:

| Ordem | Arquivo | Categoria | Objetos Criados |
| :---: | :--- | :--- | :--- |
| **Pré** | [schema.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/schema.sql) | DDL Estrutural | Criação do banco `banco_fenix`, 19 tabelas do domínio, PKs, FKs, UKs e Constraints `CHECK` |
| **Pré** | [data.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/data.sql) | DML de Carga | Massa de dados realista (clientes PF/PJ, funcionários, usuários, tintas, OS, itens, rotas e cronogramas) |
| **1º** | [etapa3_01_functions.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_01_functions.sql) | **4 Functions** | `dbo.fn_valor_os`, `dbo.fn_itens_atrasados`, `dbo.fn_prioridade_clientes`, `dbo.fn_auditoria_timeline_os` |
| **2º** | [etapa3_02_views.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_02_views.sql) | **3 Views** | `dbo.vw_painel_ordens_servico`, `dbo.vw_produtividade_funcionario`, `dbo.vw_retrabalho_mensal` |
| **3º** | [etapa3_03_procedures.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_03_procedures.sql) | **3 Procedures** | `dbo.sp_ajustar_estoque_tinta`, `dbo.sp_cancelar_ordem_servico`, `dbo.sp_replanejar_cronograma` |
| **4º** | [etapa3_04_triggers.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_04_triggers.sql) | **3 Triggers** | `dbo.trg_itens_sincronizar_valor_os`, `dbo.trg_historicos_status_os_proteger`, `dbo.trg_historicos_status_itens_sincronizar_item_e_os` |
| **Extra** | [backup_restore.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/backup_restore.sql) | **Backup & Restore** | Backup Full online com verificação física (`restore verifyonly`) e Restore seguro (`single_user with rollback immediate`) |

---

## Parte A — Explicação Detalhada e Visão de Mercado dos 13 Recursos

### 1. Functions ([etapa3_01_functions.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_01_functions.sql))

#### 1.1 `dbo.fn_valor_os(@nr_os int)` — *Scalar Function* (`returns decimal(18, 2)`)
* **Tabelas atreladas:** `dbo.Itens`, `dbo.HistoricosStatusItens`, `dbo.StatusItens`.
* **Problema real de negócio que resolve:** Na modelagem da Fênix, a coluna `OrdensDeServico.valor_os` armazena o valor total da OS, mas um item não possui uma coluna fixa de status — seu status atual é sempre o registro mais recente na tabela `HistoricosStatusItens` (RN-HIT-03). Pelas regras RN-OS-14 e RN-OS-15, itens cancelados não entram na soma e itens ainda sem preço (`null`) contam como `0.00`. Calcular isso fora do banco obriga a aplicação a buscar todos os itens e fazer uma consulta de histórico para cada peça.
* **Como resolve no SQL Server:** Executa um `sum(i.valor_total_item)` filtrando, via `outer apply (select top (1) si.status ... order by hsi.data_hora desc)`, apenas os itens cujo status atual seja diferente de `'Cancelada'`, retornando `isnull(@valor_calculado, 0.00)`. Além de poder ser chamada diretamente pelo Java, é a peça central reutilizada pela procedure `dbo.sp_cancelar_ordem_servico` e pelas triggers `dbo.trg_itens_sincronizar_valor_os` e `dbo.trg_historicos_status_itens_sincronizar_item_e_os`.

#### 1.2 `dbo.fn_itens_atrasados(@data_referencia date)` — *Inline Table-Valued Function* (`returns table`)
* **Tabelas atreladas:** `dbo.Itens`, `dbo.OrdensDeServico`, `dbo.Pessoas`, `dbo.HistoricosStatusItens`, `dbo.StatusItens`, `dbo.ItensRotasProcessos`, `dbo.RotasProcessos`.
* **Problema real de negócio que resolve:** No chão de fábrica, o gestor precisa de um radar de SLA mostrando quais peças estão com `prazo_entrega` vencido e ainda não foram entregues/executadas (RN-ITE-22), sabendo imediatamente quantos dias de atraso cada peça acumula e em qual etapa/operador ela está parada.
* **Como resolve no SQL Server:** Implementada como *Inline Table-Valued Function* (que o otimizador do SQL Server expande no plano de execução como uma View parametrizada de alta performance), filtra os itens com `prazo_entrega < @data_referencia` cujo status atual (`cross apply`) não seja `'Executada'` nem `'Cancelada'`. Com um segundo `outer apply`, localiza a primeira etapa não concluída (`data_conclusao is null order by irp.ordem asc`) e retorna os dias de atraso (`datediff`), o processo pendente (`'Jateamento'`, `'Banho'` ou `'Pintura'`) e o nome do operador responsável.

#### 1.3 `dbo.fn_prioridade_clientes(@prioridade_minima int, @prioridade_maxima int)` — *Multi-Statement Table-Valued Function com Variável Tabela*
* **Tabelas atreladas:** `dbo.Clientes`, `dbo.Pessoas`, `dbo.OrdensDeServico`, `dbo.HistoricosStatusOs`, `dbo.StatusOs`.
* **Problema real de negócio que resolve:** Pela regra RN-CLI-09 definida pela Fênix, a prioridade comercial do cliente (escala de `0` a `100`) não é digitada manualmente: ela é proporcional ao faturamento histórico acumulado de OS não canceladas daquele cliente em relação ao cliente de maior faturamento da empresa:
  $$\text{Prioridade} = \text{Mínimo} + \text{round}\left(\frac{\text{Faturamento do Cliente} \times (\text{Máximo} - \text{Mínimo})}{\text{Maior Faturamento}}, 0\right)$$
* **Como resolve no SQL Server:** Cumpre o requisito de *Multi-Statement Table-Valued Function* em um cenário real onde o cálculo exige **duas passadas sobre os dados**:
  1. **1ª Passada (Variável `@faturamento table`):** Consolida a soma de `valor_os` de todas as OS cujo último status em `HistoricosStatusOs` seja diferente de `'Cancelada'`, agrupando por `c.pessoa_codigo`.
  2. **2ª Passada (Tabela de retorno `@prioridades table`):** Obtém `@maior_faturamento = max(total_faturado)` da variável tabela e calcula o score proporcional normalizado para cada cliente, tratando divisão por zero quando ninguém faturou.

#### 1.4 `dbo.fn_auditoria_timeline_os(@nr_os int)` — *Inline Table-Valued Function* (`returns table`)
* **Tabelas atreladas:** `dbo.OrdensDeServico`, `dbo.Usuarios`, `dbo.HistoricosStatusOs`, `dbo.StatusOs`, `dbo.Itens`, `dbo.HistoricosStatusItens`, `dbo.StatusItens`, `dbo.ItensRotasProcessos`, `dbo.RotasProcessos`, `dbo.Pessoas`.
* **Problema real de negócio que resolve:** Em auditorias de qualidade (ISO / PDCA) ou atendimento a reclamações de clientes, é necessário reconstruir a linha do tempo completa (*Audit Trail*) de uma Ordem de Serviço. Como os eventos estão distribuídos em três estruturas distintas (histórico da OS, histórico de cada item e apontamentos de início/conclusão de etapas no chão de fábrica), consultar cada tabela separadamente dificulta enxergar a sequência real dos fatos.
* **Como resolve no SQL Server:** Unifica via `union all` os 4 tipos de eventos cronológicos da OS (`'Ordem de Serviço'`, `'Peça (Item)'`, início e conclusão de `'Etapa de Produção'` com cálculo dos minutos trabalhados) e numera a sequência exata com `row_number() over (order by eventos.data_hora asc, ...)`, entregando um dossiê completo da OS em uma única consulta.

---

### 2. Views ([etapa3_02_views.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_02_views.sql))

#### 2.1 `dbo.vw_painel_ordens_servico`
* **Tabelas atreladas:** `dbo.OrdensDeServico`, `dbo.Clientes`, `dbo.Pessoas`, `dbo.PessoasFisicas`, `dbo.PessoasJuridicas`, `dbo.Usuarios`, `dbo.HistoricosStatusOs`, `dbo.StatusOs`, `dbo.Itens`, `dbo.HistoricosStatusItens`, `dbo.StatusItens`.
* **Problema real de negócio que resolve:** A listagem principal de Ordens de Serviço e o Dashboard precisam exibir dados da OS, tipo do cliente (`PF` ou `PJ`), CPF ou CNPJ, login de quem abriu, status atual da OS e o resumo quantitativo das peças daquela OS (quantas peças no total, quantas executadas, canceladas, em aberto, atrasadas e se há item sem preço deixando o `valor_os` incompleto — RN-OS-15).
* **Como resolve no SQL Server:** Combina `inner join` e `left join` na hierarquia de herança de `Pessoas` (`coalesce(pf.cpf, pj.cnpj)`) com dois blocos `outer apply`: o primeiro busca o status atual da OS em `HistoricosStatusOs`; o segundo agrega em uma única varredura todos os contadores de itens da OS e a flag `valor_incompleto`. Elimina completamente o problema de *N+1 queries* e alimenta o gráfico **"Ordens de serviço por status"**.

#### 2.2 `dbo.vw_produtividade_funcionario`
* **Tabelas atreladas:** `dbo.ItensRotasProcessos`, `dbo.Funcionarios`, `dbo.Pessoas`, `dbo.RotasProcessos`.
* **Problema real de negócio que resolve:** A gerência industrial precisa medir a produtividade diária da fábrica e o desempenho individual dos operadores (*Jatistas*, *Pintores*, *Auxiliares*) por tipo de processo (`'Jateamento'`, `'Banho'`, `'Pintura'`), contabilizando quantas etapas foram concluídas, quantas peças distintas foram atendidas, quantas etapas foram retrabalho e o tempo médio gasto por etapa em minutos.
* **Como resolve no SQL Server:** Filtra as etapas concluídas (`irp.data_conclusao is not null`), verifica via `cross apply` se a mesma etapa já havia ocorrido em ordem anterior no mesmo item (`eh_retrabalho`) e agrupa por `cast(irp.data_conclusao as date)`, funcionário, cargo e processo. Calcula `count(*) as etapas_concluidas`, `count(distinct irp.item_codigo) as itens_atendidos`, `sum(datediff(minute, ...))` e `avg(...) as minutos_medios_por_etapa`. Alimenta simultaneamente **dois gráficos do Dashboard** (*"Produção diária"* e *"Produtividade por funcionário"*) e **dois relatórios exportáveis para Excel (CSV)**.

#### 2.3 `dbo.vw_retrabalho_mensal`
* **Tabelas atreladas:** `dbo.Itens`, `dbo.ItensRotasProcessos`, `dbo.HistoricosStatusItens`, `dbo.StatusItens`.
* **Problema real de negócio que resolve:** Cálculo do indicador oficial do **PDCA de Gestão da Qualidade (RN-IRP-09)**: percentual mensal de peças finalizadas (`'Executada'`) que precisaram repetir pelo menos uma etapa do processo produtivo (retrabalho), além do impacto financeiro (`valor_executado`) do mês.
* **Como resolve no SQL Server:** Utiliza uma *Common Table Expression* (`with cte_itens_executados as (...)`) que seleciona os itens com status atual `'Executada'` e `data_finalizacao` preenchida, verifica com `exists (select 1 from dbo.ItensRotasProcessos ... group by irp.rota_processo_codigo having count(*) > 1)` se houve repetição de etapa na peça e agrupa por mês (`datefromparts(year, month, 1)`), calculando o percentual exato em `decimal(5, 2)`. Alimenta o gráfico **"% de retrabalho por mês"** do Dashboard.

---

### 3. Stored Procedures ([etapa3_03_procedures.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_03_procedures.sql))

#### 3.1 `dbo.sp_ajustar_estoque_tinta`
* **Assinatura:** `(@tinta_codigo int, @quantidade decimal(10, 2), @estoque_resultante decimal(10, 2) output)`
* **Tabela atrelada:** `dbo.Tintas`.
* **Problema real de negócio que resolve:** Movimentação concorrente de estoque de tintas em kg (RN-TIN-14 e RN-TIN-15: `@quantidade > 0` para entrada de compra; `@quantidade < 0` para baixa de consumo na pintura). Se dois operadores registrarem baixa da mesma tinta simultaneamente lendo o saldo na aplicação, ocorre o clássico *Lost Update* (ou saldo negativo).
* **Como resolve no SQL Server:** Protegida por `set xact_abort on` e bloco `begin try ... begin tran ... commit tran ... end try begin catch ... rollback tran; throw; end catch`, aplica bloqueio pessimista de linha (`with (updlock, rowlock)`) no `select` de leitura do saldo, valida se a tinta existe (`50002`) e se o saldo ficaria negativo (`50003`), atualiza `estoque_atual` e devolve o novo saldo em `@estoque_resultante output`.

#### 3.2 `dbo.sp_cancelar_ordem_servico`
* **Assinatura:** `(@nr_os int, @itens_cancelados int output)`
* **Tabelas atreladas:** `dbo.OrdensDeServico`, `dbo.HistoricosStatusOs`, `dbo.StatusOs`, `dbo.Itens`, `dbo.HistoricosStatusItens`, `dbo.StatusItens`, `dbo.Cronogramas`, `dbo.CronogramasItens`, `dbo.ItensRotasProcessos`.
* **Problema real de negócio que resolve:** O cancelamento de uma OS (RN-OS-03, RN-OS-05 e RN-OS-15) é uma operação transacional complexa que afeta múltiplas tabelas em cascata: precisa impedir o cancelamento de OS já concluída, cancelar todos os itens ainda não executados, retirar as etapas pendentes desses itens dos cronogramas futuros da fábrica, gravar o status `'Cancelada'` na OS e atualizar o `valor_os` (descontando os itens cancelados).
* **Como resolve no SQL Server:** Executa todas as 5 etapas em uma única transação atômica *set-based* no banco:
  1. Trava a linha da OS (`with (updlock, rowlock)`) e valida se existe e se não está `'Concluída'` (`50011`) ou `'Cancelada'` (`50012`);
  2. Insere em lote (`insert into dbo.HistoricosStatusItens ... select`) o status `'Cancelada'` para todas as peças da OS cujo status atual não seja `'Executada'` nem `'Cancelada'`, tratando colisões de segundo na chave primária composta (`data_hora`);
  3. Remove (`delete ci from dbo.CronogramasItens ...`) as etapas não concluídas daquela OS que estavam planejadas em cronogramas de hoje em diante;
  4. Grava `'Cancelada'` em `dbo.HistoricosStatusOs`;
  5. Atualiza `OrdensDeServico.valor_os = dbo.fn_valor_os(@nr_os)` e retorna a contagem de peças canceladas em `@itens_cancelados output`.

#### 3.3 `dbo.sp_replanejar_cronograma`
* **Assinatura:** `(@data_origem date, @data_destino date, @usuario_codigo int, @etapas_movidas int output)`
* **Tabelas atreladas:** `dbo.Cronogramas`, `dbo.CronogramasItens`, `dbo.ItensRotasProcessos`, `dbo.Itens`, `dbo.Usuarios`, `dbo.HistoricosStatusItens`, `dbo.HistoricosStatusOs`.
* **Problema real de negócio que resolve:** Rotina de fechamento diário de PCP (Planejamento e Controle da Produção). Ao final do turno em `@data_origem`, as etapas planejadas no cronograma que não puderam ser concluídas precisam ser transferidas em lote para o cronograma do próximo dia útil (`@data_destino`), preservando a ordem da fila sem duplicar etapas já agendadas.
* **Como resolve no SQL Server:** Dentro de transação explícita com `updlock, holdlock`, localiza o cronograma de origem e busca (ou cria automaticamente, caso não exista) o cronograma de `@data_destino`. Em seguida, usa uma CTE com `row_number() over (order by ci.posicao asc)` para transferir todas as etapas ainda abertas (`irp.data_conclusao is null`, item ativo e OS aberta) que ainda não estejam no destino, numerando a coluna `posicao` sequencialmente a partir do último item existente no destino.

---

### 4. Triggers ([etapa3_04_triggers.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/etapa3_04_triggers.sql))

#### 4.1 `dbo.trg_itens_sincronizar_valor_os` (`after insert, update, delete` em `dbo.Itens`)
* **Tabelas atreladas:** `dbo.Itens` (tabela alvo), `dbo.Tintas`, `dbo.OrdensDeServico`.
* **Problema real de negócio que resolve:**
  1. **Validação cruzada de cor (RN-ITE-04):** Quando o item possui tinta definida (`tinta_codigo is not null`), a cor cadastrada na tinta (`Tintas.cor_codigo`) deve ser obrigatoriamente a mesma cor pedida no item (`Itens.cor_codigo`). Uma constraint `CHECK` comum não consegue validar colunas de outra tabela.
  2. **Sincronização automática do subtotal da OS (RN-OS-14 e RN-ITE-08):** Qualquer inclusão, alteração de preço ou exclusão de item precisa refletir imediatamente em `OrdensDeServico.valor_os`.
* **Como resolve no SQL Server:** Utilizando as tabelas lógicas `inserted` e `deleted` em modo *set-based*:
  1. Se houver qualquer linha em `inserted` cuja cor divirja da cor da tinta em `dbo.Tintas`, executa `rollback tran` e lança o erro `50110`;
  2. Reúne todas as OS afetadas (`select os_codigo from inserted union select os_codigo from deleted`) e atualiza automaticamente `os.valor_os = dbo.fn_valor_os(os.nr_os)`.

#### 4.2 `dbo.trg_historicos_status_os_proteger` (`after insert, update, delete` em `dbo.HistoricosStatusOs`)
* **Tabelas atreladas:** `dbo.HistoricosStatusOs` (tabela alvo), `dbo.StatusOs`.
* **Problema real de negócio que resolve:** A tabela `HistoricosStatusOs` é uma trilha de auditoria imutável (*append-only*, RN-HOS-01) e uma OS encerrada (`'Concluída'` ou `'Cancelada'`) jamais pode ser reaberta ou receber novos status (RN-OS-03). Sem proteção no banco, um comando SQL direto poderia apagar o histórico ou reabrir uma OS finalizada.
* **Como resolve no SQL Server:**
  1. Se a tabela lógica `deleted` contiver registros (`if exists (select 1 from deleted)`), significa que houve tentativa de `UPDATE` ou `DELETE`: a trigger desfaz a transação (`rollback tran`) e dispara o erro `50101`;
  2. Em operações de `INSERT`, consulta via `cross apply` o status imediatamente anterior da OS e, caso seja `'Concluída'` ou `'Cancelada'`, desfaz a transação e dispara o erro `50102`.

#### 4.3 `dbo.trg_historicos_status_itens_sincronizar_item_e_os` (`after insert, update, delete` em `dbo.HistoricosStatusItens`)
* **Tabelas atreladas:** `dbo.HistoricosStatusItens` (tabela alvo), `dbo.StatusItens`, `dbo.Itens`, `dbo.OrdensDeServico`.
* **Problema real de negócio que resolve:** Garante três regras críticas sempre que o status de uma peça muda:
  1. O histórico de status do item também é imutável (RN-HIT-01);
  2. Quando um item passa para `'Executada'`, a coluna `Itens.data_finalizacao` deve ser preenchida automaticamente (RN-ITE-26), respeitando `data_finalizacao >= data_entrada` (`ck_itens_datas_fim`);
  3. Quando um item passa para `'Cancelada'`, ele deve sair imediatamente da soma de `OrdensDeServico.valor_os` (RN-OS-15).
* **Como resolve no SQL Server:**
  1. Bloqueia qualquer `UPDATE` ou `DELETE` verificando `if exists (select 1 from deleted)` (erro `50120`);
  2. Atualiza `Itens.data_finalizacao` para todos os itens que receberam o status `'Executada'` na tabela lógica `inserted`;
  3. Recalcula `OrdensDeServico.valor_os = dbo.fn_valor_os(os.nr_os)` para todas as OS pai dos itens presentes em `inserted`.

---

## Parte B — Auditoria do Projeto Java e Mapeamento de Integração

> **Nota da Auditoria:** Foi realizada uma varredura completa em modo **somente leitura** sobre todas as classes dos pacotes `controllers`, `services`, `repositories`, `entities`, `dtos` e nos scripts de front-end (`static/js`). Abaixo está o diagnóstico exato de onde a lógica hoje roda em memória no Java e como o time deve plugar cada um dos **13 recursos do SGBD**.

### 1. Diagnóstico dos Gráficos do Dashboard e Relatórios (Já Estruturados pelo Grupo!)

A auditoria em [DashboardController.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/controllers/DashboardController.java#L18-L34), [DashboardService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/DashboardService.java#L51-L144), [dashboard.js](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/resources/static/js/paginas/dashboard.js#L43-L45) e [relatorios.js](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/resources/static/js/paginas/relatorios.js#L108-L129) confirmou que **o grupo já preparou toda a infraestrutura visual (Chart.js) e o contrato REST (`GET /api/dashboard/graficos/{nome}`)** para **4 gráficos na tela inicial**, que são alimentados diretamente pelas **3 Views da Etapa 3**:

| Nome do Gráfico na API (`{nome}`) | Título no Front-End (`dashboard.js`) | Tipo Chart.js | View SQL Server que Alimenta o Gráfico | Consulta SQL Executada no `DashboardService.grafico()` |
| :--- | :--- | :---: | :--- | :--- |
| `producao-diaria` | **Produção diária** *(Etapas concluídas por dia)* | Barra Vertical | `dbo.vw_produtividade_funcionario` | `select convert(varchar(5), data_producao, 103) as rotulo, sum(etapas_concluidas) as valor from dbo.vw_produtividade_funcionario group by data_producao order by data_producao asc` |
| `retrabalho-mensal` | **Retrabalho mensal** *(% de itens com etapa repetida - PDCA)* | Linha | `dbo.vw_retrabalho_mensal` | `select right('0' + cast(mes as varchar(2)), 2) + '/' + cast(ano as varchar(4)) as rotulo, percentual_retrabalho as valor from dbo.vw_retrabalho_mensal order by mes_referencia asc` |
| `etapas-por-funcionario` | **Produtividade por funcionário** *(Etapas concluídas por operador)* | Barra Horizontal | `dbo.vw_produtividade_funcionario` | `select funcionario_nome as rotulo, sum(etapas_concluidas) as valor from dbo.vw_produtividade_funcionario group by funcionario_nome order by valor desc` |
| `os-por-status` | **Distribuição de OS por status** *(Ordens de serviço por status)* | Rosca (Doughnut) | `dbo.vw_painel_ordens_servico` | `select status_atual as rotulo, count(*) as valor from dbo.vw_painel_ordens_servico group by status_atual order by valor desc` |

> **Observação Arquitetural Importante:** Em vez de criar uma 4ª view redundante apenas para somar etapas por dia (`vw_producao_diaria`) ou contar OS por status (`vw_os_por_status`), a arquitetura utiliza `dbo.vw_produtividade_funcionario` (que já possui a granularidade diária por operador) e `dbo.vw_painel_ordens_servico` (que já consolida o status atual de cada OS). Assim, **apenas 3 Views ricas resolvem todos os 4 gráficos do Dashboard, os cards de resumo e os relatórios CSV**!

---

### 2. Mapa Completo de Auditoria: Onde Substituir Lógica em Memória pelos Recursos do SGBD

A tabela abaixo localiza **classe por classe, método por método e linha por linha** onde a aplicação Java atualmente processa dados em memória e onde cada recurso da Etapa 3 se conecta:

| Recurso SQL Server | Classe e Método Java Atual (Linhas) | Endpoint REST Atrelado | Como o Java Faz Hoje (Problema / Gargalo em Memória) | O que Muda com a Integração do Recurso SQL |
| :--- | :--- | :--- | :--- | :--- |
| **`dbo.fn_valor_os`** *(Scalar Function)* | [ValorOsService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ValorOsService.java#L35-L55) `recalcular(os)` e `itensQueContam(os)` | Acionado em `POST /api/ordens`, `POST /api/ordens/{n}/itens`, `PUT /api/itens/{c}` e `POST /api/itens/{c}/cancelar` | Faz `itemRepository.findByOsOrderByCodigo(os)` e, para cada item, chama `statusService.statusAtual(i)` (1 `SELECT` por item) para somar `BigDecimal` em memória no Java. | A soma passa a ser feita por `select dbo.fn_valor_os(:nrOs)` (e, com as triggers ativas, o próprio banco já atualiza `OrdensDeServico.valor_os` automaticamente). |
| **`dbo.fn_itens_atrasados`** *(Inline TVF)* | [ItemService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ItemService.java#L308-L313) `listarAtrasados()` + [ItemRepository.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/repositories/ItemRepository.java#L37) | `GET /api/itens/atrasados` ([ItemController.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/controllers/ItemController.java#L52-L55)) | Busca no banco **todos** os itens com `prazoEntrega < hoje` (mesmo os já entregues há meses) e filtra um por um na RAM com `.filter(i -> !statusService.statusAtual(i).isEncerrado())`. | Uma única query nativa `select * from dbo.fn_itens_atrasados(:hoje)` já devolve apenas os itens realmente em aberto, com `dias_atraso`, `etapa_pendente` e `operador_etapa`. |
| **`dbo.fn_prioridade_clientes`** *(Multi-Statement TVF)* | [PrioridadeClienteService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/PrioridadeClienteService.java#L53-L82) `calcularTodas()` e `deCliente()` | `GET /api/clientes` ([ClienteService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ClienteService.java#L216-L221)) e `POST /api/ordens` | Carrega `ordemServicoRepository.findAll()` (todas as OS do banco), faz 1 query de status para cada OS em loop, monta `HashMap` de faturamento e depois varre `clienteRepository.findAll()`. | Substitui todo o método `calcularTodas()` por `select * from dbo.fn_prioridade_clientes(:min, :max)`, eliminando centenas de queries a cada abertura da tela de Clientes. |
| **`dbo.fn_auditoria_timeline_os`** *(Inline TVF)* | [OrdemServicoService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/OrdemServicoService.java#L198-L202) `historico(numero)` | `GET /api/ordens/{numero}/historico` ([OrdemServicoController.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/controllers/OrdemServicoController.java#L79-L82)) | Hoje retorna apenas as linhas de `HistoricosStatusOs`, sem visibilidade de quando cada peça mudou de status ou quando cada etapa foi executada no chão de fábrica. | Permite expor a linha do tempo unificada (OS + Itens + Etapas de Produção com operador e duração) via `select * from dbo.fn_auditoria_timeline_os(:nrOs)`. |
| **`dbo.vw_painel_ordens_servico`** *(View)* | [DashboardService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/DashboardService.java#L75-L87) `resumo()` e `grafico("os-por-status")` + [OrdemServicoService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/OrdemServicoService.java#L182-L186) `listar()` | `GET /api/dashboard/resumo`, `GET /api/dashboard/graficos/os-por-status` e `GET /api/ordens` | No `resumo()`, faz `ordemServicoRepository.findAll()` e chama `statusService.statusAtual(os)` em loop para contar OS abertas e por status. | Alimenta diretamente o gráfico `os-por-status` (linha 123 do `DashboardService`) e permite carregar o resumo e a listagem de OS em uma única consulta sem *N+1*. |
| **`dbo.vw_produtividade_funcionario`** *(View)* | [DashboardService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/DashboardService.java#L119-L126) `grafico("producao-diaria")` e `grafico("etapas-por-funcionario")` | `GET /api/dashboard/graficos/producao-diaria` e `GET /api/dashboard/graficos/etapas-por-funcionario` | Já integrada no `DashboardService.java` (linhas 119–120 e 125–126) e consumida em `relatorios.js` (linhas 109–128). | Entrega as séries prontas de etapas concluídas por dia e por operador, além de tempo trabalhado e retrabalhos por processo. |
| **`dbo.vw_retrabalho_mensal`** *(View)* | [DashboardService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/DashboardService.java#L121-L122) `grafico("retrabalho-mensal")` + [ItemRotaProcessoService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ItemRotaProcessoService.java#L282-L302) | `GET /api/dashboard/graficos/retrabalho-mensal` e `GET /api/indicadores/retrabalho` | Já integrada no gráfico `retrabalho-mensal` (linhas 121–122 do `DashboardService`); no `ItemRotaProcessoService`, o cálculo mensal pode ler direto da View. | Calcula no SQL Server a taxa PDCA mensal (`percentual_retrabalho`) e o `valor_executado` sobre os itens finalizados. |
| **`dbo.sp_ajustar_estoque_tinta`** *(Procedure)* | [TintaService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/TintaService.java#L116-L137) `ajustarEstoque(codigo, dto)` | `POST /api/tintas/{codigo}/estoque` ([TintaController.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/controllers/TintaController.java#L54-L57)) | Faz `SELECT` com lock no JPA, calcula `atual.add(quantidade)` no Java, valida negativo e faz `UPDATE`. | Substitui a leitura + cálculo + gravação por uma chamada atômica `exec dbo.sp_ajustar_estoque_tinta`, recebendo `@estoque_resultante output`. |
| **`dbo.sp_cancelar_ordem_servico`** *(Procedure)* | [OrdemServicoService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/OrdemServicoService.java#L165-L172) `cancelar(numero)` + [ItemService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ItemService.java#L238-L244) | `POST /api/ordens/{numero}/cancelar` ([OrdemServicoController.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/controllers/OrdemServicoController.java#L74-L77)) | Cancela item por item em loop no Java, recalcula a OS em memória e **não limpa** as etapas dos itens cancelados nos cronogramas futuros. | Cancela a OS e todos os itens abertos em lote, limpa etapas pendentes de cronogramas futuros e atualiza `valor_os` em uma única transação. |
| **`dbo.sp_replanejar_cronograma`** *(Procedure)* | [CronogramaService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/CronogramaService.java#L42-L199) (novo método `replanejar`) | Novo endpoint sugerido: `POST /api/cronogramas/replanejar` em [CronogramaController.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/controllers/CronogramaController.java) | Não havia rotina em lote para rolar etapas não concluídas de um dia para o outro; exigia reinserção manual etapa por etapa. | Move todas as etapas pendentes de `@data_origem` para `@data_destino`, criando o cronograma de destino se necessário e renumerando a fila. |
| **`dbo.trg_itens_sincronizar_valor_os`** *(Trigger)* | Disparada automaticamente nos `save()` de [ItemService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ItemService.java#L130) (linhas 130, 170, 193) | Transparente (qualquer `POST`/`PUT` em `/api/ordens` ou `/api/itens`) | Se algum código alterasse `Itens` sem chamar `valorOsService.recalcular(os)`, o `valor_os` ficava divergente. | O próprio SGBD garante a compatibilidade `cor_codigo` (item × tinta) e recalcula `OrdensDeServico.valor_os` no ato do `INSERT`/`UPDATE`/`DELETE`. |
| **`dbo.trg_historicos_status_os_proteger`** *(Trigger)* | Disparada automaticamente nos `save()` de [StatusService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/StatusService.java#L182-L188) | Transparente (`POST /api/ordens/{n}/pausar`, `retomar`, `concluir`, `cancelar`) | A regra de histórico *append-only* (RN-HOS-01) e proibição de reabrir OS concluída (RN-OS-03) existia apenas na camada Java. | Bloqueia no SGBD qualquer `UPDATE`/`DELETE` em `HistoricosStatusOs` (`50101`) e qualquer transição após `'Concluída'`/`'Cancelada'` (`50102`). |
| **`dbo.trg_historicos_status_itens_sincronizar_item_e_os`** *(Trigger)* | Disparada automaticamente nos `save()` de [StatusService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/StatusService.java#L190-L196) | Transparente (`POST /api/etapas-item/{c}/concluir`, `POST /api/itens/{c}/cancelar`) | O preenchimento de `data_finalizacao` (linhas 148–153 de `StatusService`) e o recálculo de `valor_os` no cancelamento eram feitos manualmente no Java. | Bloqueia `UPDATE`/`DELETE` no histórico do item (`50120`), preenche `Itens.data_finalizacao` ao entrar `'Executada'` e recalcula `valor_os` automaticamente. |

---

### 3. Guia Comentado para o Time Java: Como Plugar Cada Chamada no Código

Abaixo estão os trechos exatos e comentados de como a equipe pode plugar as Functions, Views e Stored Procedures nos arquivos do projeto usando **`JdbcClient`** (já injetado no projeto em `DashboardService.java`) ou **`@Query(nativeQuery = true)`**:

#### 3.1 Plugando `dbo.fn_prioridade_clientes` em [PrioridadeClienteService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/PrioridadeClienteService.java#L53-L76)
```java
// Time: Em PrioridadeClienteService.java, injete o JdbcClient e substitua o loop em memória do método calcularTodas() (linhas 53 a 76) por:
@Transactional(readOnly = true)
Map<Integer, Integer> calcularTodas() {
    Map<Integer, Integer> prioridades = new HashMap<>();
    jdbcClient.sql("select cliente_codigo, prioridade from dbo.fn_prioridade_clientes(:min, :max)")
            .param("min", parametros.getPrioridadeMinima())
            .param("max", parametros.getPrioridadeMaxima())
            .query((rs) -> {
                prioridades.put(rs.getInt("cliente_codigo"), rs.getInt("prioridade"));
            });
    return prioridades;
}
```

#### 3.2 Plugando `dbo.fn_valor_os` em [ValorOsService.java](file:///d:/Workspace/Projects/projeto-extensao-2026-2/src/main/java/br/fatec/projetoextensao/services/ValorOsService.java#L35-L44)
```java
// Time: Em ValorOsService.java (linhas 35 a 44), o cálculo em memória pode consultar diretamente a Scalar Function:
void recalcular(OrdemServicoEntity os) {
    BigDecimal soma = jdbcClient.sql("select dbo.fn_valor_os(:nrOs)")
            .param("nrOs", os.getNumero())
            .query(BigDecimal.class)
            .single();
    os.setValorOS(Valores.normalizar(soma));
    ordemServicoRepository.save(os);
}
```

#### 3.3 Plugando `dbo.fn_itens_atrasados` e `dbo.fn_auditoria_timeline_os` nos Repositories
```java
// Time: Em ItemRepository.java, adicione a chamada nativa para a Inline Table-Valued Function de atrasos:
@Query(nativeQuery = true, value = "select * from dbo.fn_itens_atrasados(:dataRef) order by dias_atraso desc")
List<Map<String, Object>> buscarRadarAtrasados(@Param("dataRef") LocalDate dataRef);

// Time: Em OrdemServicoRepository.java, adicione a chamada nativa para a Timeline completa de Auditoria da OS:
@Query(nativeQuery = true, value = "select * from dbo.fn_auditoria_timeline_os(:nrOs) order by sequencia asc")
List<Map<String, Object>> buscarTimelineAuditoria(@Param("nrOs") int nrOs);
```

#### 3.4 Plugando as 3 Stored Procedures nos Services (`TintaService`, `OrdemServicoService`, `CronogramaService`)
```java
// 1. Em TintaService.ajustarEstoque() (linhas 124 a 137) — chamando dbo.sp_ajustar_estoque_tinta:
BigDecimal novoSaldo = jdbcClient.sql("""
        declare @saldo decimal(10, 2);
        exec dbo.sp_ajustar_estoque_tinta
            @tinta_codigo = :codigo,
            @quantidade = :qtd,
            @estoque_resultante = @saldo output;
        select @saldo;
        """)
        .param("codigo", codigo)
        .param("qtd", quantidade)
        .query(BigDecimal.class)
        .single();

// 2. Em OrdemServicoService.cancelar() (linhas 165 a 172) — chamando dbo.sp_cancelar_ordem_servico:
Integer itensCancelados = jdbcClient.sql("""
        declare @cancelados int;
        exec dbo.sp_cancelar_ordem_servico
            @nr_os = :nrOs,
            @itens_cancelados = @cancelados output;
        select @cancelados;
        """)
        .param("nrOs", numero)
        .query(Integer.class)
        .single();

// 3. Em CronogramaService.java — novo método para chamar dbo.sp_replanejar_cronograma:
@PreAuthorize(Perfis.QUALQUER)
@Transactional
public int replanejar(LocalDate dataOrigem, LocalDate dataDestino) {
    int usuarioCodigo = usuarioService.usuarioLogadoEntidade().getFuncionarioCodigo();
    return jdbcClient.sql("""
            declare @movidas int;
            exec dbo.sp_replanejar_cronograma
                @data_origem = :origem,
                @data_destino = :destino,
                @usuario_codigo = :usuario,
                @etapas_movidas = @movidas output;
            select @movidas;
            """)
            .param("origem", dataOrigem)
            .param("destino", dataDestino)
            .param("usuario", usuarioCodigo)
            .query(Integer.class)
            .single();
}
```

---

## Parte C — Roteiro de Demonstração ao Vivo no SQL Server (SSMS)

Os blocos abaixo estão **100% prontos para copiar, colar e executar com `F5` no SQL Server Management Studio (SSMS)** durante a apresentação para a professora, sem depender da aplicação Java estar aberta. Todos os testes de escrita utilizam transações com `rollback tran` ao final para que você possa demonstrar quantas vezes quiser sem sujar a base de dados.

### Bloco 1 — Demonstração das 4 Functions (`etapa3_01_functions.sql`)
```sql
use banco_fenix
go

-- 1.1 scalar function: comparando o valor gravado na os com o cálculo em tempo real da função
select
    nr_os,
    valor_os               as valor_gravado,
    dbo.fn_valor_os(nr_os) as valor_calculado_funcao
from dbo.OrdensDeServico
where nr_os in (1, 2, 10);

-- 1.2 inline table-valued function: radar de peças atrasadas com dias de atraso, etapa parada e operador
select *
from dbo.fn_itens_atrasados('2026-10-25')
order by dias_atraso desc, nr_os asc;

-- 1.3 multi-statement table-valued function (com variável @faturamento table): ranking de prioridade comercial (0 a 100)
select *
from dbo.fn_prioridade_clientes(0, 100)
order by prioridade desc, faturamento desc;

-- 1.4 inline table-valued function: linha do tempo completa de auditoria (os + peças + chão de fábrica) da os #1
select *
from dbo.fn_auditoria_timeline_os(1)
order by sequencia asc;
go
```

---

### Bloco 2 — Demonstração das 3 Views (`etapa3_02_views.sql`)
```sql
use banco_fenix
go

-- 2.1 view 1 (vw_painel_ordens_servico): painel gerencial completo e agrupamento do gráfico de os por status
select *
from dbo.vw_painel_ordens_servico
order by nr_os desc;

select
    status_atual,
    count(*)      as total_os,
    sum(valor_os) as soma_valor
from dbo.vw_painel_ordens_servico
group by status_atual
order by total_os desc;

-- 2.2 view 2 (vw_produtividade_funcionario): produtividade diária por operador/processo e ranking de operadores
select *
from dbo.vw_produtividade_funcionario
order by data_producao desc, funcionario_nome asc;

select
    funcionario_nome,
    cargo,
    sum(etapas_concluidas)   as total_etapas,
    sum(itens_atendidos)     as total_pecas,
    sum(minutos_trabalhados) as total_minutos
from dbo.vw_produtividade_funcionario
group by funcionario_nome, cargo
order by total_etapas desc;

-- 2.3 view 3 (vw_retrabalho_mensal): indicador mensal de qualidade (pdca)
select *
from dbo.vw_retrabalho_mensal
order by mes_referencia desc;
go
```

---

### Bloco 3 — Demonstração das 3 Stored Procedures (`etapa3_03_procedures.sql`)
```sql
use banco_fenix
go

-- 3.1 procedure 1 (sp_ajustar_estoque_tinta): entrada de +5,00 kg e baixa de -5,00 kg com retorno output
declare @saldo decimal(10, 2);

exec dbo.sp_ajustar_estoque_tinta
    @tinta_codigo       = 1,
    @quantidade         = 5.00,
    @estoque_resultante = @saldo output;
select @saldo as saldo_apos_entrada_5kg;

exec dbo.sp_ajustar_estoque_tinta
    @tinta_codigo       = 1,
    @quantidade         = -5.00,
    @estoque_resultante = @saldo output;
select @saldo as saldo_restaurado;
go

-- 3.2 procedure 2 (sp_replanejar_cronograma): move etapas pendentes de 22/10 para 25/10 e renumera a fila
begin tran;

declare @movidas int;
exec dbo.sp_replanejar_cronograma
    @data_origem    = '2026-10-22',
    @data_destino   = '2026-10-25',
    @usuario_codigo = 1,
    @etapas_movidas = @movidas output;

select @movidas as etapas_replanejadas;

select
    c.data_planejada,
    ci.posicao,
    ci.item_rota_processo_codigo,
    ci.qtd_planejado
from dbo.Cronogramas            as c
inner join dbo.CronogramasItens as ci on ci.cronograma_codigo = c.codigo
where c.data_planejada = '2026-10-25'
order by ci.posicao asc;

rollback tran;
go

-- 3.3 procedure 3 (sp_cancelar_ordem_servico): cancela a os #10 em cascata (itens + cronograma + valor_os)
begin tran;

declare @itens_cancelados int;
exec dbo.sp_cancelar_ordem_servico
    @nr_os            = 10,
    @itens_cancelados = @itens_cancelados output;

select
    @itens_cancelados as itens_cancelados,
    nr_os,
    status_atual,
    valor_os,
    qtd_itens_em_aberto,
    qtd_itens_cancelados
from dbo.vw_painel_ordens_servico
where nr_os = 10;

rollback tran;
go
```

---

### Bloco 4 — Demonstração das 3 Triggers (`etapa3_04_triggers.sql`)
```sql
use banco_fenix
go

-- 4.1 trigger de proteção (trg_historicos_status_os_proteger): bloqueia tentativa de apagar histórico (erro 50101)
begin try
    delete from dbo.HistoricosStatusOs where os_codigo = 1;
end try
begin catch
    select error_number() as codigo_erro, error_message() as mensagem_trigger;
end catch;
go

-- 4.2 trigger de proteção (trg_historicos_status_os_proteger): bloqueia tentativa de reabrir a os #1 já concluída (erro 50102)
begin try
    insert into dbo.HistoricosStatusOs (os_codigo, status_codigo, data_hora)
    values (1, 2, sysdatetime());
end try
begin catch
    select error_number() as codigo_erro, error_message() as mensagem_trigger;
end catch;
go

-- 4.3 trigger de sincronização (trg_itens_sincronizar_valor_os): soma +r$ 500,00 num item da os #10 e vê o valor_os atualizar sozinho
begin tran;

select nr_os, valor_os as valor_os10_antes
from dbo.OrdensDeServico
where nr_os = 10;

update top (1) dbo.Itens
set valor_total_item = valor_total_item + 500.00
where os_codigo = 10;

select nr_os, valor_os as valor_os10_depois_automatico
from dbo.OrdensDeServico
where nr_os = 10;

rollback tran;
go

-- 4.4 trigger de histórico do item (trg_historicos_status_itens_sincronizar_item_e_os): bloqueia delete no histórico do item (erro 50120)
begin try
    delete from dbo.HistoricosStatusItens where item_codigo = 1;
end try
begin catch
    select error_number() as codigo_erro, error_message() as mensagem_trigger;
end catch;
go
```

---

## Parte D — Explicação Técnica da Rotina de Backup e Restore ([backup_restore.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/backup_restore.sql))

O script [backup_restore.sql](file:///d:/Workspace/Projects/projeto-extensao-2026-2/docs/database/backup_restore.sql) implementa uma rotina de **Continuidade de Negócios e Recuperação de Desastres (DRP)** seguindo as boas práticas de administração de bancos de dados SQL Server em produção:

### 1. Descoberta Dinâmica de Diretório (`xp_instance_regread`)
Em vez de fixar um caminho rígido como `C:\temp\...` (que falha caso a pasta não exista ou o serviço do SQL Server não tenha permissão de escrita NTFS nela), o script consulta o registro da própria instância do SQL Server (`HKEY_LOCAL_MACHINE\Software\Microsoft\MSSQLServer\MSSQLServer\BackupDirectory`) para obter a pasta oficial de backup configurada na máquina e monta o caminho `@caminho_arquivo = concat(@diretorio_backup, N'\banco_fenix_full.bak')`.

### 2. Rotina de Backup Full Online com Verificação de Integridade
* **`backup database banco_fenix to disk = @caminho_arquivo with format, init, name = ..., stats = 10;`**:
  * Realiza um backup completo (*Full Backup*) a quente (sem interromper o uso do sistema);
  * As opções `format, init` garantem um cabeçalho de mídia limpo, sobrescrevendo o arquivo `.bak` anterior de demonstração sem acumular múltiplos *backup sets* no mesmo arquivo;
  * `stats = 10` exibe o progresso no console a cada 10% concluídos.
* **`restore verifyonly from disk = @caminho_arquivo;`**:
  * Prática essencial de mercado: um backup só é considerado válido depois de verificado. O comando `restore verifyonly` lê todo o arquivo `.bak` recém-gravado em disco e confere os *checksums* de páginas e cabeçalhos para atestar que o arquivo está íntegro e legível para restauração.

### 3. Rotina de Restore Seguro sem Bloqueio de Sessões (`single_user with rollback immediate`)
O erro mais comum ao tentar restaurar um banco no SQL Server é o erro `3101` (*"Exclusive access could not be obtained because the database is in use"*), causado por conexões abertas do pool HikariCP do Spring Boot ou por abas abertas no próprio SSMS. Para garantir uma restauração infalível:
1. **`use master`**: Posiciona a sessão fora do banco `banco_fenix`.
2. **`alter database banco_fenix set single_user with rollback immediate;`**: Coloca o banco em modo de usuário único exclusivamente para a sessão atual do DBA, revertendo (`rollback immediate`) transações incompletas e desconectando instantaneamente todas as conexões ativas da aplicação Java ou de outros usuários.
3. **`restore database banco_fenix from disk = @caminho_arquivo with replace, recovery, stats = 10;`**:
   * `replace`: permite sobrescrever o banco existente;
   * `recovery`: aplica o *redo/undo* do log de transações e já deixa o banco operacional (pronto para uso).
4. **`alter database banco_fenix set multi_user;`**: Devolve imediatamente o banco para o modo multiusuário padrão, permitindo que o Spring Boot e os demais usuários voltem a se conectar normalmente.
