# Integração da Etapa 3 do banco com o sistema Java

**O que este documento explica.** A Etapa 3 criou 13 objetos no SQL Server: 4 functions, 3 views, 3 procedures e 3 triggers, mais a rotina de backup e restore. Esta entrega fez o sistema Java e as telas **usarem** esses objetos. Aqui está cada mudança, onde ela foi feita e por quê.

- Data: 18/11/2026.
- Branch de origem: `feature/ajustes-e-pente-fino`, commit `6d4943c`.
- Scripts do grupo: em `docs/database/`. **Nenhum script foi alterado.**

---

## 1. Resumo em uma página

| Objeto do banco | Quem chama no Java | Onde aparece para o usuário |
|---|---|---|
| `dbo.fn_valor_os` | `ValorOsService.sincronizar`, que confere o valor gravado pelos triggers | Valor da OS em todas as telas |
| `dbo.fn_itens_atrasados` | `ItemService.listarAtrasados` e `DashboardService.resumo` | Sino de alertas, alertas do dashboard e relatório *Itens atrasados* |
| `dbo.fn_prioridade_clientes` | `PrioridadeClienteService` | Prioridade na tela Clientes e prioridade inicial da OS |
| `dbo.fn_auditoria_timeline_os` | `OrdemServicoService.auditoria` | Nova aba **Auditoria** na tela da OS |
| `dbo.vw_painel_ordens_servico` | `OrdemServicoService.painel`, `ValorOsService.valorIncompleto` e `DashboardService` | Lista de OS, Clientes, Peças, relatório de OS e gráfico *OS por status* |
| `dbo.vw_produtividade_funcionario` | `DashboardService` e `IndicadorService` | Gráficos *Produção diária* e *Produtividade por funcionário*, aba **Produtividade** e 2 relatórios |
| `dbo.vw_retrabalho_mensal` | `DashboardService` e `IndicadorService` | Gráfico *Retrabalho mensal*, indicador do mês e relatório **Retrabalho mensal** |
| `dbo.sp_ajustar_estoque_tinta` | `TintaService.ajustarEstoque` | Ajuste de estoque na tela Tintas |
| `dbo.sp_cancelar_ordem_servico` | `OrdemServicoService.cancelar` | Botão *Cancelar OS*, agora com avisos |
| `dbo.sp_replanejar_cronograma` | `CronogramaService.replanejar` | Novo botão **Replanejar pendências** no Cronograma |
| `trg_itens_sincronizar_valor_os` | É automático: o Java só relê a OS | Valor da OS; cor da tinta conferida pelo banco |
| `trg_historicos_status_os_proteger` | É automático | Histórico da OS imutável; OS encerrada não recebe status |
| `trg_historicos_status_itens_sincronizar_item_e_os` | É automático: o Java só relê o item | Data de finalização do item; valor da OS |
| `backup_restore.sql` | `BancoDadosService` | Nova aba **Configurações > Banco de dados**, só para ADMIN |

> **Observação.** A Parte B do `LEIA_ME_BD.md` descreve o `DashboardService` como "já integrado" às views. No código recebido isso ainda não existia: os gráficos respondiam "Em desenvolvimento" e nenhuma classe usava `JdbcClient`. Esta entrega é a que faz essa integração.

---

## 2. Como achar cada mudança no código

Toda mudança tem um comentário no próprio código, no mesmo padrão que o grupo já usava, com um **código de rastreio** `[E3-xx]`:

```java
// INCLUÍDO (18/11/2026) [E3-12]: o que foi incluído e por quê.
// ALTERADO (18/11/2026) [E3-08]: era <como estava>; agora <como ficou>, porque <motivo>.
// REMOVIDO (18/11/2026) [E3-09]: o que saiu, por que saiu e quem faz isso agora.
```

Para achar todos os lugares de uma mudança, pesquise o código na IDE (Ctrl+Shift+F), por exemplo `E3-12`. As faixas de códigos são:

| Faixa | Parte do sistema |
|---|---|
| E3-01 a E3-19 | Back-end |
| E3-31 a E3-42 | Telas |
| E3-50 a E3-54 | Testes |
| E3-60 | Este documento |

---

## 3. Seis conceitos para entender a integração

### 3.1 Quem é o "dono" da coluna

Antes, o Java calculava e gravava três valores. Agora quem calcula e grava é o banco:

- **`OrdensDeServico.valor_os`**: gravado pelos triggers e pela procedure de cancelamento.
- **`Itens.data_finalizacao`**: gravado pelo trigger do histórico do item.
- **`Tintas.estoque_atual`**: gravado pela procedure de ajuste.

Se o Java continuasse escrevendo essas colunas, os dois disputariam o mesmo dado. Um exemplo de como isso daria errado:

1. Ao mudar a prioridade da OS, o Hibernate manda um `UPDATE` com **todas** as colunas da entidade.
2. Nesse `UPDATE` iria o `valor_os` antigo, que estava na memória.
3. Esse valor antigo apagaria o valor que o trigger tinha acabado de calcular.

Por isso as três colunas ficaram assim nas entidades (mudança E3-07):

```java
@Column(name = "valor_os", insertable = false, updatable = false) // o Hibernate lê, mas nunca escreve
```

### 3.2 Flush e refresh: o Java e o trigger em sincronia

O Hibernate não grava na hora: ele junta as alterações e manda tudo ao banco no *flush*. O trigger só roda quando o comando chega ao banco. E o valor que o trigger grava não aparece sozinho no objeto Java que está na memória.

Por isso, depois de mexer num item, o Java faz dois passos (`ValorOsService.sincronizar`):

```text
item alterado na memória
   └─ flush()   → o UPDATE do item chega ao banco → o trigger roda e grava valor_os
   └─ refresh() → o Java relê a OS do banco e passa a ter o valor_os do trigger
```

O mesmo acontece em `StatusService` quando o item fica "Executada": o trigger preenche `data_finalizacao` e o Java relê o item.

### 3.3 Function e view: lidas com SQL comum (`JdbcClient`)

Functions de tabela e views devolvem linhas de relatório, não entidades. Por isso foram lidas com `JdbcClient`, todas num lugar só: `RotinasBancoRepository`. Cada linha vira direto o DTO que a API devolve.

```java
jdbc.sql("select ... from dbo.fn_itens_atrasados(:referencia)").param("referencia", Date.valueOf(hoje))...
```

O `JdbcClient` usa a **mesma conexão e a mesma transação** do JPA, mas não provoca o *flush* do Hibernate. Por isso todo método do repositório faz o *flush* antes de consultar (`descarregarJpa()`).

### 3.4 Procedure com parâmetro OUTPUT (`CallableStatement`)

As procedures são chamadas assim (`ProcedimentosBancoSqlServer`):

```java
jdbcTemplate.execute("{call dbo.sp_ajustar_estoque_tinta(?, ?, ?)}", (CallableStatementCallback<BigDecimal>) cs -> {
    cs.setInt(1, tintaCodigo);                       // @tinta_codigo
    cs.setBigDecimal(2, quantidade);                 // @quantidade
    cs.registerOutParameter(3, Types.DECIMAL, 2);    // @estoque_resultante OUTPUT
    cs.execute();
    return cs.getBigDecimal(3);
});
```

### 3.5 Por que o Java confere as regras antes de chamar a procedure

As procedures abrem `BEGIN TRAN` **dentro** da transação do Spring:

- **Se dão certo:** o `COMMIT` delas não confirma nada sozinho. Quem confirma tudo é o Spring, no fim do método.
- **Se dão erro:** o `ROLLBACK` do `CATCH` desfaz a transação **inteira**, inclusive o que o Java gravou antes no mesmo método.

Por isso o Java confere as regras antes de chamar (OS já cancelada, saldo insuficiente, datas) e mostra a mesma mensagem de sempre. A procedure confere de novo: é a última barreira, por exemplo quando dois usuários agem ao mesmo tempo.

### 3.6 Erro do banco vira mensagem na tela

Quando uma regra é violada, os scripts fazem `THROW 5xxxx, 'mensagem', 1`. A classe `ErrosBanco` acha esse código na cadeia da exceção. Ela olha `getCause` e também `getNextException`, porque o SQL Server pode mandar o erro 266 junto. Depois converte o erro, e a tela recebe **a mesma mensagem que o grupo escreveu no script**:

| Código | Origem | HTTP |
|---|---|---|
| 50001, 50003 | `sp_ajustar_estoque_tinta` (quantidade zero, saldo insuficiente) | 400 |
| 50002 | `sp_ajustar_estoque_tinta` (tinta não encontrada) | 404 |
| 50010 | `sp_cancelar_ordem_servico` (OS não encontrada) | 404 |
| 50011, 50012 | `sp_cancelar_ordem_servico` (OS concluída, OS já cancelada) | 400 |
| 50020, 50021 | `sp_replanejar_cronograma` (datas, usuário) | 400 |
| 50101, 50102 | `trg_historicos_status_os_proteger` | 409 |
| 50110 | `trg_itens_sincronizar_valor_os` (cor diferente da tinta) | 400 |
| 50120 | `trg_historicos_status_itens_sincronizar_item_e_os` | 409 |
| 8115 | Estouro aritmético (decimal) | 400 |
| 208, 2812, 4121 citando um dos 13 objetos | Objeto da Etapa 3 não instalado | **503**, com o nome do objeto e do script |

---

## 4. Todas as mudanças, uma a uma

### 4.1 Infraestrutura e classes novas do back-end

| Código | Arquivo | O que foi feito | Por quê |
|---|---|---|---|
| E3-01 | `pom.xml` | INCLUÍDO `<resources>`, que copia os 5 scripts para o classpath, em `bancodados/` | A aba Banco de dados executa **os arquivos do grupo**, e não uma cópia colada no Java. Assim existe uma fonte só. As duas primeiras entradas repetem, sem mudança, a configuração do parent do Spring Boot, porque declarar `<resources>` substitui a lista herdada |
| E3-02 | `enums/ObjetoBancoEnum.java` (novo) | Catálogo dos 13 objetos com nome, tipo no `sys.objects`, script, finalidade e onde o sistema usa | O nome de cada objeto fica escrito uma vez só. O catálogo é usado na conferência ao iniciar, na aba Banco de dados e na tradução do erro "objeto não instalado" |
| E3-03 | `exceptions/ErrosBanco.java` e `ObjetoBancoAusenteException.java` (novos) | Tradução dos códigos `THROW` e dos erros 208, 2812 e 4121 | Ver o conceito 3.6 |
| E3-04 | `exceptions/GlobalExceptionHandler.java` | INCLUÍDO o tratamento de `DataAccessException`, `PersistenceException`, `TransactionSystemException` e `ObjetoBancoAusenteException`. ALTERADO `integridade()`, que agora consulta o `ErrosBanco` primeiro | O erro do trigger pode chegar no *flush*, no `JdbcTemplate` ou no commit. Antes, todos esses casos viravam "erro interno" (500) |
| E3-05 | `repositories/RotinasBancoRepository.java` (novo) | Leitura das 4 functions e das 3 views, mais a consulta de apoio ao cancelamento | Ver o conceito 3.3 |
| E3-06 | `repositories/ProcedimentosBanco.java` e `ProcedimentosBancoSqlServer.java` (novos) | Interface com as 3 procedures e a implementação com `{call ...}` | Ver o conceito 3.4. A interface existe porque o H2 dos testes não tem procedure com OUTPUT; lá entra uma imitação (E3-52) |
| E3-16 | `repositories/CatalogoBancoRepository.java`, `services/ScriptsBanco.java` e `services/BancoDadosService.java` (novos) | Situação dos objetos (`sys.objects` e `sys.triggers`), último backup (`msdb`), divisão dos scripts pelo `GO`, instalação, backup e restore | Ver a seção 6 |
| E3-17 | `services/VerificacaoObjetosBanco.java` (novo) | Ao iniciar, confere no SQL Server se os 13 objetos existem e se algum trigger está desabilitado, e escreve o resultado no log | Sem os objetos o sistema não funciona direito. O log diz exatamente o que falta. A conferência só avisa e não instala sozinha: criar objetos é decisão do administrador |

### 4.2 Entidades

| Código | Arquivo | O que foi feito | Por quê |
|---|---|---|---|
| E3-07 | `OrdemServicoEntity.valorOS` | ALTERADO para `insertable = false, updatable = false` | O valor é gravado pelos triggers e pela procedure (conceito 3.1). A OS nasce com o `DEFAULT 0` do script |
| E3-07 | `ItemEntity.dataFinalizacao` | ALTERADO para `insertable = false, updatable = false` | Quem grava é o trigger do histórico do item. Sem a mudança, registrar a entrega (`nr_ose`) apagaria a data gravada |
| E3-07 | `TintaEntity.estoque` | ALTERADO para `insertable = false, updatable = false` | Quem grava é a procedure de ajuste (RN-TIN-14). Alterar o cadastro da tinta não pode desfazer um ajuste feito ao mesmo tempo |

### 4.3 Services

| Código | Arquivo e método | Antes | Agora | Por quê |
|---|---|---|---|---|
| E3-08 | `ValorOsService` | `recalcular()` somava em Java e gravava `valor_os` | `sincronizar()` faz *flush*, *refresh* e confere com `fn_valor_os`. Se o valor gravado for diferente, registra um aviso no log | O banco é o dono do valor. A conferência denuncia trigger ausente ou desabilitado |
| E3-08 | `ValorOsService.valorIncompleto` | Laço nos itens, em Java | Coluna `valor_incompleto` da `vw_painel_ordens_servico` | A tela da OS e a lista de OS passam a usar a mesma regra (RN-OS-15) |
| E3-08 | `ItemService.incluir`, `atualizar` e `cancelar`; `OrdemServicoService.abrir` | Chamavam `recalcular` | Chamam `sincronizar` | O trigger já gravou o valor; o Java só relê |
| E3-09 | `StatusService.mudarStatus(item)` | Preenchia `data_finalizacao` em Java | REMOVIDO o bloco. Para "Executada", faz *flush* e *refresh* do item | O trigger preenche a data, com a mesma regra: nunca antes da entrada |
| E3-12 | `StatusService.conferirMudanca` | As conferências ficavam dentro de `mudarStatus` | O mesmo código, separado num método próprio | O cancelamento confere as regras antes da procedure (conceito 3.5) |
| E3-10 | `PrioridadeClienteService` | Laço em todas as OS, com uma consulta de status por OS | Chama `fn_prioridade_clientes(mínimo, máximo)` | A regra é a mesma. O `ROUND(x, 0)` do SQL Server arredonda a metade para cima, como o `HALF_UP` do Java. Os testes antigos de prioridade continuam passando sem mudança |
| E3-11 | `ItemService.listarAtrasados` | Filtro em Java, devolvendo `ItemResponse` | Chama `fn_itens_atrasados(hoje)` e devolve `ItemAtrasadoResponse` | Traz também cliente, dias de atraso, próxima etapa e operador |
| E3-12 | `ItemService.cancelarItensDaOs` | Cancelava os itens um a um | REMOVIDO | Quem cancela os itens agora é a procedure, em lote |
| E3-12 | `OrdemServicoService.cancelar` | Status, itens e valor gravados passo a passo em Java | Trava a OS, confere as regras, lê os cronogramas que serão afetados, chama `sp_cancelar_ordem_servico` e relê a OS | Tudo numa transação no banco. **Novidade:** a procedure tira dos cronogramas futuros as etapas da OS cancelada, e o Java avisa quantas saíram e qual cronograma ficou vazio |
| E3-12 | `OrdemServicoService.painel` e `auditoria` | Não existiam | Leem a `vw_painel_ordens_servico` e a `fn_auditoria_timeline_os` | A lista de OS fica leve; a auditoria dá rastreabilidade |
| E3-13 | `TintaService.ajustarEstoque` | `t.setEstoque(novo)` e `save` | Mantém as conferências em Java, chama `sp_ajustar_estoque_tinta` e relê a tinta | A procedure trava a linha e garante o saldo. O Java continua mostrando "saldo atual X kg", que a procedure não informa |
| E3-14 | `CronogramaService.replanejar` | Não existia | Confere as datas, conta as etapas que a procedure copiaria e só então chama `sp_replanejar_cronograma` com o usuário logado | Ver a observação 7.1 sobre cronograma vazio |
| E3-15 | `DashboardService` | Gráficos respondiam "Em desenvolvimento"; o resumo percorria todas as OS | Os 4 gráficos e o resumo leem as views e a `fn_itens_atrasados` | Os dados aparecem sem mudar o contrato da API |
| E3-15 | `IndicadorService` (novo) | Não existia | Produtividade por período e retrabalho mês a mês | Alimenta a aba Produtividade e os relatórios |

O **% de retrabalho de um período qualquer** (por exemplo, de 05/11 a 18/11) continua em Java, no `ItemRotaProcessoService.percentualRetrabalho`. A view agrupa por mês e não responde a um intervalo de dias. Um teste confere que, para o mês atual, a view e o Java dão o mesmo número.

### 4.4 Controllers e API (E3-18) e DTOs (E3-19)

| Método e rota | Situação | Objeto do banco |
|---|---|---|
| `GET /api/ordens/painel` | Nova | `vw_painel_ordens_servico` |
| `GET /api/ordens/{n}/auditoria` | Nova | `fn_auditoria_timeline_os` |
| `POST /api/ordens/{n}/cancelar` | Rota igual; a resposta traz `avisos` | `sp_cancelar_ordem_servico` |
| `GET /api/itens/atrasados` | Rota igual; o corpo agora é `ItemAtrasadoResponse` | `fn_itens_atrasados` |
| `POST /api/cronogramas/replanejar` | Nova. Corpo: `{"dataOrigem": "2026-11-18", "dataDestino": "2026-11-19"}` | `sp_replanejar_cronograma` |
| `GET /api/indicadores/produtividade?inicio=&fim=` | Nova | `vw_produtividade_funcionario` |
| `GET /api/indicadores/retrabalho/mensal?meses=12` | Nova | `vw_retrabalho_mensal` |
| `GET /api/dashboard/graficos/{nome}` | Rota igual; agora com dados | As 3 views |
| `GET /api/banco/situacao` | Nova, só ADMIN | `sys.objects` e `msdb` |
| `POST /api/banco/objetos/instalar` | Nova, só ADMIN | Os 4 scripts da Etapa 3 |
| `POST /api/banco/backup` | Nova, só ADMIN | `backup_restore.sql`, rotina 1 |
| `POST /api/banco/restauracao` | Nova, só ADMIN. Corpo: `{"confirmacao": "RESTAURAR banco_fenix"}` | `backup_restore.sql`, rotina 2 |

Os DTOs novos são:

- `ItemAtrasadoResponse`
- `EventoAuditoriaResponse`
- `PainelOsResponse`
- `ProdutividadeResponse`
- `RetrabalhoMensalResponse`
- `ReplanejamentoRequest` e `ReplanejamentoResponse`
- `ObjetoBancoResponse` e `SituacaoBancoResponse`
- `BackupResponse`
- `OperacaoBancoResponse`
- `RestauracaoRequest`

Os campos que já existiam em `ItemResponse` e `OsResponse` mantiveram **o mesmo nome**. Assim as telas trocaram de endpoint quase sem mudar código.

### 4.5 Telas (E3-31 a E3-42)

| Código | Arquivo | O que mudou para o usuário |
|---|---|---|
| E3-31 | `componentes/os.js` | `osAtrasada` também entende a linha do painel (`qtdItensAtrasados`) |
| E3-32 | `paginas/ordens.js` | A lista lê o painel. A coluna "Prazo" virou **"Peças"**: executadas/total e quantas estão atrasadas |
| E3-33 | `paginas/ordem.js` | Nova aba **Auditoria** (linha do tempo). O cancelamento mostra os avisos (itens cancelados, cronogramas afetados ou vazios). A nota do valor diz que ele é calculado pelo banco |
| E3-34 | `paginas/dashboard.js`, `graficos.js` e `config.js` | Os gráficos aparecem com dados e com o período na descrição. Os alertas de atraso mostram dias de atraso, cliente e próxima etapa. Quando não há dados, aparece o motivo devolvido pela API |
| E3-35 | `layout.js` | O sino mostra os dias de atraso |
| E3-36 | `paginas/relatorios.js` | *Ordens de Serviço* passa a ter as contagens da view. *Itens atrasados* ganha dias, próxima etapa e operador. *Produção diária* e *Produtividade* foram **liberados** (antes "Em desenvolvimento"). Há um relatório novo: **Retrabalho mensal** |
| E3-37 | `paginas/cronograma.js` | Botão **Replanejar pendências**, no topo e em cada dia. Aviso quando o cronograma ficou sem etapas |
| E3-38 | `paginas/configuracoes.js` | Nova aba **Banco de dados**: objetos instalados, último backup, instalar, backup e restore com confirmação digitada. As abas Parâmetros e Acessos foram atualizadas |
| E3-39 | `icones.js` | Ícone `database` (Lucide) |
| E3-40 | `paginas/producao.js` | Nova aba **Produtividade**: etapas, retrabalho e minutos por funcionário e processo |
| E3-41 e E3-42 | `referencias.js`, `paginas/pecas.js` e `paginas/clientes.js` | Usam o painel em vez da OS completa |

---

## 5. Testes (E3-50 a E3-54)

Daqui não foi possível acessar um SQL Server, e a rede do ambiente bloqueia o Maven Central. Por isso a integração foi testada assim:

1. **H2 com os objetos imitados.**
   - `src/test/resources/etapa3-h2.sql` cria, no H2, as functions (como `CREATE ALIAS` para `RotinasH2`), as views e os triggers (`TriggersH2`), com os **mesmos nomes, colunas, regras e códigos de erro** dos scripts.
   - As procedures são imitadas por `ProcedimentosBancoH2`, que só vale no perfil `test`.
   - O `application-test.properties` carrega esse arquivo depois do `schema-h2.sql`.
   - **É uma imitação:** confere o Java, mas não substitui o teste no SQL Server.
2. **Testes novos.** Ao todo são 28:
   - `Etapa3IntegracaoTest` (17): triggers mexendo no banco por fora, colunas que o Hibernate não regrava, as 3 procedures e seus erros, functions, views comparadas com o cálculo em Java, gráficos, restrição ao ADMIN e divisão dos scripts pelo `GO`.
   - `GlobalExceptionHandlerTest` (+1): tradução dos códigos 5xxxx, 8115 e 208.
   - `ApiTest` (+1): os endpoints novos pelo HTTP.
   - `SegurancaTest` (+1): OPERADOR recebe 403 em `/api/banco`.
   - `Etapa3SqlServerIT` (8): só roda quando é pedido (ver abaixo).
3. **Testes ajustados.**
   - `OrdemServicoTest.rnIte22`: o tipo da lista de atrasados mudou.
   - `ApiTest.dashboardPelaApi`: os gráficos agora têm dados.

**Resultado, compilando com JDK 21 sem Maven:**

| Testes | Quantidade |
|---|---|
| Encontrados | 165 |
| Passaram | 156 |
| Pulados (o IT do SQL Server, que é desligado por padrão) | 8 |
| Falhou | 1: `ProjetoextensaoApplicationTests.contextLoads` |

O `contextLoads` precisa do SQL Server ligado e já falhava antes desta mudança, pelo mesmo motivo.

As telas também foram abertas num navegador (Chromium) com o sistema rodando sobre o H2 imitado. Foram percorridos estes pontos, sem erro de JavaScript:

- login, dashboard com os 4 gráficos e sino;
- lista de OS e aba Auditoria;
- cancelamento com avisos e cronograma vazio;
- replanejamento;
- aba Banco de dados, que mostra a mensagem do H2;
- relatórios e aba Produtividade.

### O que o grupo precisa rodar

No Windows (PowerShell), na pasta do projeto:

```powershell
# 1. Testes automáticos (H2). O "clean" apaga a pasta target, que ainda guarda classes compiladas antes desta mudança.
.\mvnw.cmd clean test

# 2. Teste no SQL Server real: banco ligado e os scripts da Etapa 3 instalados.
#    De preferência numa CÓPIA do banco: os dados do teste são desfeitos no fim, mas não arrisque a produção.
#    As aspas são necessárias no PowerShell: sem elas, ele parte "-Dfenix.it.sqlserver=true" no primeiro ponto.
.\mvnw.cmd test "-Dtest=Etapa3SqlServerIT" "-Dfenix.it.sqlserver=true"
```

No Git Bash ou no Linux, são os mesmos comandos com `./mvnw` e sem as aspas.

**Contagem esperada pelo Maven.** O Surefire, plugin que roda os testes no `mvnw test`, só inclui por padrão as classes que começam com `Test` ou terminam em `Test`, `Tests` ou `TestCase`. O `Etapa3SqlServerIT` termina em `IT`, então nem entra na contagem do passo 1. Aparecem **157 testes**: os 156 que passaram aqui e o `contextLoads`. Este passa se o SQL Server do `application.properties` estiver ligado e falha, como já falhava, se não estiver. No passo 2 o IT é chamado pelo nome (`-Dtest`), o que dispensa essa regra, e aí ele roda.

O `Etapa3SqlServerIT` confere quatro pontos:

1. Os 13 objetos estão instalados, com o tipo certo, e os triggers estão habilitados.
2. **Os nomes das colunas das functions e views são os que o Java lê.** Este é o ponto mais importante: se uma coluna mudar de nome no script, o teste acusa.
3. Os códigos de erro das procedures são os esperados.
4. Um fluxo completo de OS (abrir, cancelar item, cancelar OS) passa pelos triggers e pela procedure.

### Roteiro manual no sistema (SQL Server)

1. Instale os objetos: rode os scripts `etapa3_01` a `etapa3_04` no SSMS **ou** use Configurações > Banco de dados > *Instalar ou atualizar objetos*. A aba deve mostrar **13 de 13**.
2. Abra uma OS com dois itens com valor. O valor total é a soma; quem gravou foi o trigger.
3. Cancele um item: o valor cai. Execute o outro (inicie e conclua as etapas): a data de finalização aparece.
4. Abra a aba **Auditoria** da OS: os eventos aparecem em ordem.
5. Planeje uma etapa no cronograma de amanhã e cancele a OS: os avisos dizem que o cronograma ficou vazio.
6. No Cronograma, use **Replanejar pendências** de hoje para amanhã.
7. Faça um ajuste de estoque de tinta, com entrada e baixa maior que o saldo.
8. Veja o Dashboard (4 gráficos) e os relatórios *Produção diária*, *Produtividade* e *Retrabalho mensal*.
9. Em Configurações > Banco de dados, clique em **Fazer backup agora**: aparecem as mensagens do SQL Server. Restaure **só num banco de teste**.

---

## 6. Backup e restore pelo sistema: como funciona e cuidados

- O sistema executa **o próprio `backup_restore.sql`**, dividido pelo `GO`:
  - para o backup, o `use master` e a rotina 1;
  - para o restore, o `use master` e a rotina 2.
- **A conexão é separada do pool**, aberta no banco `master`. Há três motivos:
  1. Um `use master` numa conexão do pool faria as próximas telas procurarem as tabelas no `master`.
  2. `BACKUP` e `RESTORE` não podem rodar dentro de transação.
  3. O `SINGLE_USER WITH ROLLBACK IMMEDIATE` derruba as conexões ao `banco_fenix`; a conexão do restore, por estar no `master`, não cai.
- **Antes e depois do restore**, as conexões do pool são descartadas (`softEvictConnections`), para nenhuma conexão derrubada ser reaproveitada.
- **Se o `RESTORE` falhar no meio**, o sistema roda `alter database banco_fenix set multi_user`, para o banco não ficar preso em modo de um usuário só.
- **Para o restore acontecer**, é preciso:
  - digitar `RESTAURAR banco_fenix`;
  - ter um backup registrado no `msdb`.
- **O arquivo `.bak` fica no servidor do SQL Server**, na pasta padrão de backup da instância, e não na máquina de quem clicou.
- **Permissões do login do sistema (hoje `sa`):**
  - criar objetos no banco;
  - `BACKUP DATABASE`, com `db_backupoperator` ou `sysadmin`;
  - `RESTORE`, com `dbcreator` ou `sysadmin`;
  - `xp_instance_regread`;
  - leitura do `msdb`.
- **Risco que sobra:** se uma conexão nova ao `banco_fenix` ocupar a vaga do `SINGLE_USER` entre o `ALTER` e o `RESTORE`, aparece o erro 3101 ("acesso exclusivo"). O sistema traduz esse erro para "feche o SSMS e outros programas e tente de novo".

---

## 7. Observações sobre os scripts do grupo

Os scripts não foram alterados. Os pontos abaixo foram tratados no Java e ficam registrados para o grupo decidir se quer mudar o SQL.

1. **`sp_replanejar_cronograma` pode criar cronograma vazio.** A procedure cria o cronograma do destino **antes** de saber se há etapa para copiar. Se não houver nenhuma, fica um cronograma sem etapas, o que fere a RN-CRO-01. O Java conta as etapas antes e só chama a procedure quando há o que mover. Correção possível no script: criar o destino só se `exists (...)` encontrar alguma etapa pendente.
2. **`sp_replanejar_cronograma` não confere se o destino é passado.** O Java recusa destino anterior a hoje (RN-CRO-06).
3. **`sp_replanejar_cronograma` copia sem apagar a origem.** A etapa fica nos dois dias, o que é bom como histórico. Mas a regra RN-CRI-05 (`somarPlanejado`) soma a etapa em **todos** os cronogramas, e depois de um replanejamento ela conta duas vezes. Fica registrado no código como OBSERVAÇÃO, e a regra pode passar a contar só de hoje em diante.
4. **`sp_cancelar_ordem_servico` pode deixar cronograma vazio.** A procedure apaga as etapas e mantém o cabeçalho do cronograma. O Java avisa qual cronograma ficou vazio, e a tela mostra o alerta e permite excluir o cronograma ou incluir outras etapas.
5. **Responsável na `fn_auditoria_timeline_os`.** Nos eventos de status, a função mostra o login de **quem abriu a OS**, porque o schema não guarda quem mudou o status. A API acrescenta `papelResponsavel` ("abriu a OS" ou "operador da etapa"), e a tela escreve "OS aberta por" ou "operador". Para registrar quem mudou o status, seria preciso uma coluna de usuário nos dois históricos.
6. **Data de "hoje".** As procedures e a view usam a data do servidor do SQL Server (`sysdatetime()`), e o Java usa a do servidor da aplicação. Em instalação na mesma máquina, as duas são iguais.
7. **Tempo das etapas.** `datediff(minute, ...)` conta viradas de minuto. Uma etapa iniciada às 10:59:50 e concluída às 11:00:10 mostra "1 min".

---

## 8. Arquivos da entrega

**Alterados:**

- `pom.xml`
- Entidades: `OrdemServicoEntity`, `ItemEntity`, `TintaEntity`
- `GlobalExceptionHandler`
- `ItemRepository` e `ItemRotaProcessoRepository` (só comentário: um método de cada ficou sem uso e foi mantido para conferência)
- Services: `ValorOsService`, `StatusService`, `PrioridadeClienteService`, `ItemService`, `OrdemServicoService`, `TintaService`, `CronogramaService`, `DashboardService`
- Controllers: `OrdemServicoController`, `ItemController`, `CronogramaController`, `IndicadorController`, `DashboardController`
- 15 arquivos JS das telas
- Testes: `OrdemServicoTest`, `ApiTest`, `GlobalExceptionHandlerTest`, `SegurancaTest`
- `application-test.properties`

**Novos:**

- `ObjetoBancoEnum`
- `ErrosBanco`, `ObjetoBancoAusenteException`
- `RotinasBancoRepository`, `ProcedimentosBanco`, `ProcedimentosBancoSqlServer`, `CatalogoBancoRepository`
- `ScriptsBanco`, `BancoDadosService`, `VerificacaoObjetosBanco`, `IndicadorService`
- `BancoDadosController`
- 12 DTOs
- Testes: `etapa3-h2.sql`, `RotinasH2`, `TriggersH2`, `ProcedimentosBancoH2`, `Etapa3IntegracaoTest`, `Etapa3SqlServerIT`
- Este documento

**Sem alteração:** os scripts `docs/database/*.sql`, o `LEIA_ME_BD.md` e o `schema-h2.sql`.
