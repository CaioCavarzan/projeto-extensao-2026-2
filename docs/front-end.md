# Front-end do Sistema Fênix (Etapa 2)

Incluído em 21/10/2026. O front fica em `src/main/resources/static` e é servido pela própria aplicação Spring Boot, na
mesma origem da API (`/api/...`). São HTML, CSS e JavaScript puros, em módulos ES, sem etapa de build e sem nada
carregado da internet: as bibliotecas (Chart.js, fonte Inter e ícones Lucide) estão em `static/vendor`, cada uma com a
sua licença. O visual segue o protótipo feito no Lovable (tema escuro, vermelho Fênix, mesma barra lateral e os mesmos
cartões), adaptado ao que o banco e as regras de negócio realmente têm.

Conferido com o Spring Boot 3.4.3 do pom original e com o 3.5.16 do pom atualizado em 21/10: 127 testes JUnit
(Services, API e segurança) e 28 fluxos de tela no navegador (Chromium), com dados sintéticos.

---

## 1. Como rodar

1. Crie o banco com `docs/database/script_banco.sql` no SQL Server. Usuário, senha e endereço do banco ficam em
   `src/main/resources/application.properties`.
2. Rode a aplicação: `mvnw.cmd spring-boot:run` no Windows (`./mvnw spring-boot:run` no Linux/macOS) ou o botão
   *Run* da classe `ProjetoextensaoApplication` no VS Code.
3. Abra **http://localhost:8080/**. Sem login, qualquer tela leva para `login.html`.

Para gerar o WAR e publicar num Tomcat externo, o endereço passa a ter o nome do contexto (ex.:
`http://localhost:8080/projetoextensao/`). Todos os endereços do front são relativos, então nada muda no código.

Como front e API estão na mesma origem, não é preciso liberar CORS. A propriedade `fenix.cors.origens` só importa se
alguém ainda usar o app do Lovable chamando esta API.

## 2. Primeiro acesso: criar o primeiro administrador

Pelas regras, só o administrador cadastra funcionários (RN-SIS-03), e só funcionário cadastrado ativa conta em
"Cadastre-se" (RN-USU-02), que sempre nasce com perfil Operador (RN-USU-03). Num banco vazio, o primeiro administrador
sai assim:

1. Cadastre o primeiro funcionário direto no banco (troque os valores entre `< >`; CPF, CEP e telefone só com dígitos;
   cargo da lista do sistema: Jatista, Pintor, Auxiliar de Produção, Secretário ou Auxiliar Administrativo):

   ```sql
   use banco_fenix;
   declare @endereco int, @pessoa int;
   insert into Enderecos (uf, cidade, cep, logradouro, nr_imovel)
        values ('SP', '<cidade>', '<cep>', '<logradouro>', '<número>');
   set @endereco = SCOPE_IDENTITY();
   insert into Pessoas (nome, email, telefone, endereco_codigo)
        values ('<nome>', '<email ou null>', '<telefone>', @endereco);
   set @pessoa = SCOPE_IDENTITY();
   insert into Funcionarios (pessoa_codigo, cpf, cargo, salario, data_admissao)
        values (@pessoa, '<cpf>', '<cargo>', <salário>, '<aaaa-mm-dd>');
   ```

2. Na tela de login, aba **Cadastre-se**: informe esse CPF e escolha login, senha, pergunta e resposta de segurança.
   A senha e a resposta são gravadas em BCrypt pela aplicação. Por isso a conta não é criada pelo SQL.
3. Promova a conta a administrador:

   ```sql
   update Usuarios set is_admin = 1 where login = '<login escolhido>';
   ```

4. Entre com esse login. Daí em diante, o administrador cadastra os funcionários em **Funcionários**. Cada um ativa a
   própria conta em Cadastre-se, e o administrador muda perfis e bloqueia acessos na aba **Usuários** dessa tela.

## 3. Visão do Administrador × visão do Operador

A tela esconde o que o perfil não pode fazer, mas a proteção de verdade está na API: cada método dos Services tem
`@PreAuthorize`. Um operador que chame uma rota de administrador direto recebe **403** com a mensagem "Ação permitida
só para administradores." (há teste para isso em `SegurancaTest` e `ApiTest`).

| Tela | Administrador | Operador |
|---|---|---|
| Dashboard | completo | completo |
| Ordens de Serviço / Nova OS | abre, altera prioridade, pausa, retoma, conclui, **cancela** | tudo, menos cancelar |
| Peças | tudo, inclusive **corrigir datas** de etapa | tudo, menos corrigir datas |
| Produção e Cronograma | completo | completo |
| Clientes | cadastra, altera, **exclui**, **corrige CPF/CNPJ** | cadastra e altera |
| Funcionários | cadastra, altera, desliga, exclui; vê **salário**; aba **Usuários** | só consulta, sem salário (o back-end nem envia) e sem a aba Usuários |
| Fornecedores | cadastra, altera, **inativa/reativa**, **exclui**, **corrige CNPJ** | cadastra e altera |
| Tintas | cadastra, altera, ajusta estoque, **exclui** | cadastra, altera e ajusta estoque |
| Relatórios | completo | completo |
| Configurações (cores, fontes, etapas, parâmetros, acessos) | completo | some do menu; pelo endereço, mostra "Acesso restrito" |
| Meu perfil | e-mail, telefone, senha e pergunta de segurança | igual |

## 4. Herança e especialização de Pessoa nas telas

| Regra do grupo | Como aparece na interface |
|---|---|
| Pessoa é obrigatoriamente cliente, funcionário ou fornecedor | Não existe tela "Pessoa". Cada cadastro (Clientes, Funcionários, Fornecedores) cria a pessoa junto com o papel. |
| Pode ser cliente e funcionário ao mesmo tempo | Cliente PF com o CPF de um funcionário (ou o contrário): a API liga o novo papel à mesma pessoa e a tela mostra o aviso. Os selos de papel aparecem no cadastro. |
| Pode ser cliente e fornecedor ao mesmo tempo | Mesmo comportamento com o CNPJ: cliente PJ ↔ fornecedor. |
| Funcionário nunca é fornecedor (e vice-versa) | A API recusa a ligação e a tela mostra a mensagem da regra. |
| Cliente é PF ou PJ, nunca os dois | O tipo é escolhido no cadastro (campos de CPF ou de CNPJ) e não muda depois. |

As regras ficam no back-end (`ClienteService`, `FuncionarioService`, `FornecedorService`, `PessoaService`). A tela só
mostra o resultado e os avisos (`MensagensResponse`). O texto de ajuda está em `js/componentes/pessoa.js`
(`TEXTO_HERANCA`).

## 5. Telas

| Arquivo | Tela | Dados (rotas principais) |
|---|---|---|
| `login.html` | Entrar, Cadastre-se e lembrete de senha (abas) | `/api/auth/*` |
| `index.html` | Dashboard: indicadores, gráficos, alertas e próximas etapas | `/api/dashboard/*`, `/api/itens/atrasados`, `/api/etapas-item/fila` |
| `ordens.html` | Lista de OS com busca e filtros | `/api/ordens` |
| `ordem-nova.html` | Abertura da OS **com os itens** (RN-OS-01) | `POST /api/ordens` |
| `ordem.html` | Detalhe da OS: itens, valor, histórico e ações | `/api/ordens/{n}` |
| `pecas.html` | Peças (itens) em cartões com foto | `/api/itens` |
| `peca.html` | Detalhe da peça: rota de produção, fotos (até 10), entrega e histórico | `/api/itens/{c}`, `/api/etapas-item/*`, `/api/fotos/*` |
| `producao.html` | Fila de etapas, retrabalho e indicador do PDCA | `/api/etapas-item/fila`, `/api/indicadores/*` |
| `cronograma.html` | Calendário do mês e fila planejada do dia | `/api/cronogramas` |
| `clientes.html` | CRUD de clientes PF/PJ (o "modelo de CRUD" da Etapa 2) | `/api/clientes` |
| `funcionarios.html` | Funcionários e aba Usuários (admin) | `/api/funcionarios`, `/api/usuarios` |
| `fornecedores.html` | Fornecedores | `/api/fornecedores` |
| `tintas.html` | Tintas e estoque | `/api/tintas` |
| `relatorios.html` | Relatórios com prévia e exportação CSV (abre no Excel) | várias |
| `configuracoes.html` | Listas de apoio, parâmetros e resumo dos acessos (admin) | `/api/cores`, `/api/fontes`, `/api/etapas`, `/api/parametros` |
| `perfil.html` | Meu perfil | `/api/perfil` |
| `404.html` | Página não encontrada | — |

A busca do topo aceita `#12` (peça 12), `12` ou `OS-12` (ordem 12) e texto (procura em Ordens de Serviço). O sino
mostra itens atrasados, OS pausadas e tintas sem estoque.

Capturas das interfaces exigidas na Etapa 2, com dados de teste fictícios, na pasta `docs/telas`:

| Arquivo | Interface |
|---|---|
| `01-login.png`, `02-cadastre-se.png`, `03-lembrete-senha.png` | Login, cadastre-se e lembrete de senha |
| `04-dashboard-admin.png`, `05-dashboard-operador.png` | Tela principal nas duas visões |
| `06-clientes-crud.png` | Modelo de CRUD (com o aviso de herança cliente + funcionário) |
| `07-operador-funcionario-somente-leitura.png` | Operador vendo funcionário: só leitura e sem salário |
| `08-meu-perfil.png` | Alteração do próprio perfil |
| `09-graficos-simulados.png` | Gráficos desenhados com uma resposta **simulada** da API (teste do que fica programado) |

## 6. Rotas da API

Todas ficam em `/api`. Erros voltam sempre no formato `ErroResponse`
(`{ status, erro, mensagens: [...], caminho, dataHora }`), com as mensagens em português da planilha de regras:
400 regra violada, 401 sem login, 403 sem perfil, 404 não encontrado, 409 conflito, 413 arquivo grande demais.

| Recurso | Método e rota | Perfil |
|---|---|---|
| Login, cadastre-se e lembrete de senha | `POST /api/auth/ativar-conta` | Público |
|  | `GET /api/auth/pergunta-seguranca` | Público |
|  | `POST /api/auth/redefinir-senha` | Público |
|  | `GET /api/auth/me` | Admin e Operador |
| Meu perfil | `GET /api/perfil` | Admin e Operador |
|  | `PUT /api/perfil` | Admin e Operador |
|  | `PUT /api/perfil/senha` | Admin e Operador |
|  | `PUT /api/perfil/pergunta-seguranca` | Admin e Operador |
| Usuários (perfis e bloqueio) | `GET /api/usuarios` | **Só Admin** |
|  | `PUT /api/usuarios/{codigo}/perfil` | **Só Admin** |
|  | `POST /api/usuarios/{codigo}/bloquear` | **Só Admin** |
|  | `POST /api/usuarios/{codigo}/desbloquear` | **Só Admin** |
| Clientes | `GET /api/clientes` | Admin e Operador |
|  | `GET /api/clientes/semelhantes` | Admin e Operador |
|  | `GET /api/clientes/{codigo}` | Admin e Operador |
|  | `POST /api/clientes/pf` | Admin e Operador |
|  | `POST /api/clientes/pj` | Admin e Operador |
|  | `PUT /api/clientes/{codigo}` | Admin e Operador |
|  | `DELETE /api/clientes/{codigo}` | **Só Admin** |
| Correção de documento (CPF/CNPJ) | `PUT /api/pessoas/{codigo}/cpf` | **Só Admin** |
|  | `PUT /api/pessoas/{codigo}/cnpj` | **Só Admin** |
| Funcionários | `GET /api/funcionarios` | Admin e Operador |
|  | `GET /api/funcionarios/{codigo}` | Admin e Operador |
|  | `POST /api/funcionarios` | **Só Admin** |
|  | `PUT /api/funcionarios/{codigo}` | **Só Admin** |
|  | `POST /api/funcionarios/{codigo}/desligar` | **Só Admin** |
|  | `DELETE /api/funcionarios/{codigo}` | **Só Admin** |
| Fornecedores | `GET /api/fornecedores` | Admin e Operador |
|  | `GET /api/fornecedores/{codigo}` | Admin e Operador |
|  | `POST /api/fornecedores` | Admin e Operador |
|  | `PUT /api/fornecedores/{codigo}` | Admin e Operador |
|  | `POST /api/fornecedores/{codigo}/inativar` | **Só Admin** |
|  | `POST /api/fornecedores/{codigo}/reativar` | **Só Admin** |
|  | `DELETE /api/fornecedores/{codigo}` | **Só Admin** |
| Tintas | `GET /api/tintas` | Admin e Operador |
|  | `GET /api/tintas/{codigo}` | Admin e Operador |
|  | `POST /api/tintas` | Admin e Operador |
|  | `PUT /api/tintas/{codigo}` | Admin e Operador |
|  | `POST /api/tintas/{codigo}/estoque` | Admin e Operador |
|  | `DELETE /api/tintas/{codigo}` | **Só Admin** |
| Cores | `GET /api/cores` | Admin e Operador |
|  | `POST /api/cores` | **Só Admin** |
|  | `PUT /api/cores/{codigo}` | **Só Admin** |
|  | `DELETE /api/cores/{codigo}` | **Só Admin** |
| Fontes de aquisição | `GET /api/fontes` | Admin e Operador |
|  | `POST /api/fontes` | **Só Admin** |
|  | `PUT /api/fontes/{codigo}` | **Só Admin** |
|  | `DELETE /api/fontes/{codigo}` | **Só Admin** |
| Etapas da rota (cadastro) | `GET /api/etapas` | Admin e Operador |
|  | `POST /api/etapas` | **Só Admin** |
|  | `PUT /api/etapas/{codigo}` | **Só Admin** |
|  | `DELETE /api/etapas/{codigo}` | **Só Admin** |
| Status | `GET /api/status/os` | Admin e Operador |
|  | `GET /api/status/itens` | Admin e Operador |
| Ordens de serviço | `GET /api/ordens` | Admin e Operador |
|  | `GET /api/ordens/{numero}` | Admin e Operador |
|  | `POST /api/ordens` | Admin e Operador |
|  | `PUT /api/ordens/{numero}/prioridade` | Admin e Operador |
|  | `POST /api/ordens/{numero}/pausar` | Admin e Operador |
|  | `POST /api/ordens/{numero}/retomar` | Admin e Operador |
|  | `POST /api/ordens/{numero}/concluir` | Admin e Operador |
|  | `POST /api/ordens/{numero}/cancelar` | **Só Admin** |
|  | `GET /api/ordens/{numero}/historico` | Admin e Operador |
|  | `POST /api/ordens/{numero}/itens` | Admin e Operador |
| Itens (peças) | `GET /api/itens` | Admin e Operador |
|  | `GET /api/itens/atrasados` | Admin e Operador |
|  | `GET /api/itens/{codigo}` | Admin e Operador |
|  | `PUT /api/itens/{codigo}` | Admin e Operador |
|  | `PUT /api/itens/{codigo}/tinta` | Admin e Operador |
|  | `POST /api/itens/{codigo}/pausar` | Admin e Operador |
|  | `POST /api/itens/{codigo}/retomar` | Admin e Operador |
|  | `POST /api/itens/{codigo}/cancelar` | Admin e Operador |
|  | `PUT /api/itens/{codigo}/entrega` | Admin e Operador |
|  | `GET /api/itens/{codigo}/historico` | Admin e Operador |
|  | `GET /api/itens/{codigo}/etapas` | Admin e Operador |
|  | `POST /api/itens/{codigo}/etapas` | Admin e Operador |
|  | `GET /api/itens/{codigo}/fotos` | Admin e Operador |
|  | `POST /api/itens/{codigo}/fotos` | Admin e Operador |
| Fotos | `GET /api/fotos/{codigo}/arquivo` | Admin e Operador |
|  | `DELETE /api/fotos/{codigo}` | Admin e Operador |
| Etapas do item (produção) | `GET /api/etapas-item/fila` | Admin e Operador |
|  | `POST /api/etapas-item/{codigo}/iniciar` | Admin e Operador |
|  | `POST /api/etapas-item/{codigo}/concluir` | Admin e Operador |
|  | `POST /api/etapas-item/{codigo}/retrabalho` | Admin e Operador |
|  | `PUT /api/etapas-item/{codigo}/datas` | **Só Admin** |
|  | `DELETE /api/etapas-item/{codigo}` | Admin e Operador |
| Indicador de retrabalho (PDCA) | `GET /api/indicadores/retrabalho` | Admin e Operador |
|  | `GET /api/indicadores/retrabalho/itens` | Admin e Operador |
| Cronograma | `GET /api/cronogramas` | Admin e Operador |
|  | `GET /api/cronogramas/dia` | Admin e Operador |
|  | `GET /api/cronogramas/{codigo}` | Admin e Operador |
|  | `POST /api/cronogramas` | Admin e Operador |
|  | `PUT /api/cronogramas/{codigo}` | Admin e Operador |
|  | `DELETE /api/cronogramas/{codigo}` | Admin e Operador |
|  | `POST /api/cronogramas/{codigo}/etapas` | Admin e Operador |
|  | `DELETE /api/cronogramas/{codigo}/etapas/{etapaCodigo}` | Admin e Operador |
|  | `PUT /api/cronogramas/{codigo}/fila` | Admin e Operador |
|  | `POST /api/cronogramas/{codigo}/fila/sugerir` | Admin e Operador |
| Dashboard | `GET /api/dashboard/resumo` | Admin e Operador |
|  | `GET /api/dashboard/graficos/{nome}` | Admin e Operador |
| Parâmetros do sistema | `GET /api/parametros` | Admin e Operador |

## 7. Dashboard: gráficos programados para a Etapa 3

Os indicadores do topo já usam dados reais (`GET /api/dashboard/resumo`). Os quatro gráficos já estão na tela e no
back-end, mas respondem `disponivel: false` até a consulta de cada um ser feita. Enquanto isso, a tela mostra o quadro
"Em desenvolvimento" com a view sugerida.

| Nome (URL) | Gráfico | Tipo | View sugerida |
|---|---|---|---|
| `producao-diaria` | Produção diária (etapas concluídas por dia) | barra | `vw_producao_diaria` |
| `retrabalho-mensal` | % de retrabalho por mês (PDCA, RN-IRP-09) | linha | `vw_retrabalho_mensal` |
| `os-por-status` | Ordens de serviço por status | rosca | `vw_os_por_status` |
| `etapas-por-funcionario` | Etapas concluídas por funcionário | barra horizontal | `vw_produtividade_funcionario` |

Formato que a tela já sabe desenhar (`GET /api/dashboard/graficos/{nome}` → `GraficoResponse`):

```json
{
  "nome": "os-por-status", "titulo": "Ordens de serviço por status", "tipo": "rosca",
  "rotulos": ["Criada", "Em produção", "Pausada"],
  "series": [ { "nome": "OS", "valores": [3, 5, 1] } ],
  "disponivel": true, "mensagem": null
}
```

Para ligar um gráfico, só o back-end muda (o front desenha qualquer resposta nesse formato). Exemplo, supondo que a
view tenha as colunas `status` e `quantidade`:

```java
// num repository (ex.: OrdemServicoRepository)
@Query(value = "select status, quantidade from vw_os_por_status", nativeQuery = true)
List<Object[]> osPorStatus();

// em DashboardService.grafico(nome), antes do retorno "Em desenvolvimento" (importe dtos.SerieGraficoResponse)
if (nome.equals("os-por-status")) {
    List<Object[]> linhas = ordemServicoRepository.osPorStatus();
    List<String> rotulos = linhas.stream().map(l -> (String) l[0]).toList();
    List<BigDecimal> valores = linhas.stream().map(l -> new BigDecimal(l[1].toString())).toList();
    return new GraficoResponse(nome, g[0], g[1], rotulos,
            List.of(new SerieGraficoResponse("OS", valores)), true, null);
}
```

Os gráficos foram testados com uma resposta simulada (`disponivel: true`): os quatro aparecem desenhados. Os
relatórios marcados "Em desenvolvimento" na tela de Relatórios dependem das mesmas consultas.

## 8. Valores definidos pela Fênix (22/10/2026) e valores ainda provisórios

| Regra | Valor | Onde fica |
|---|---|---|
| RN-CLI-09 (prioridade do cliente) | Escala de **0 a 100**, **calculada pelo sistema**: maior faturamento = maior prioridade (detalhe abaixo) | `PrioridadeClienteService`; limites em `application.properties` (`fenix.prioridade.minima` e `maxima`) |
| RN-OS-16 e RN-OS-17 (prioridade da OS) | Mesma escala, 0 a 100. A OS nasce com a prioridade do cliente e pode ser mudada à mão (número inteiro; vazio = sem prioridade) | `OrdemServicoService`; tela da OS |
| RN-ITE-17 (unidade do item) | **Peça, Conjunto, kg, Metro, Jogo**, gravadas em maiúsculas: `PEÇA`, `CONJUNTO`, `KG`, `METRO`, `JOGO` | `application.properties` (`fenix.itens.unidades`) |
| RN-TIN-08 (tipo de tinta) | 9 valores usados hoje na planilha de estoque (tabela abaixo) | `js/config.js` (`TIPOS_TINTA`) |
| RN-TIN-10 (acabamento de tinta) | 19 valores usados hoje na planilha de estoque (tabela abaixo) | `js/config.js` (`ACABAMENTOS_TINTA`) |
| RN-ROT-01 (rotas padrão) | Jateamento → Pintura; Banho → Pintura (a 3ª, "Jato, Pintura", é igual à 1ª: **a confirmar**) | `js/config.js` (`ROTAS_PADRAO`) |
| Outros | `PERGUNTAS_SEGURANCA` (as 5 do protótipo do Lovable); tentativas de login (5 e 15 min, **provisório**, RN-USU-08); limites de foto (decisão de 20/10) | `js/config.js` e `application.properties` |

**Como a prioridade do cliente é calculada.**
- Faturamento do cliente = soma do valor (`valor_os`) das OS dele que **não estão canceladas**, em todo o histórico.
  OS em andamento conta, como já faz o total mostrado na tela de Clientes.
- Prioridade = faturamento do cliente ÷ **maior faturamento entre os clientes** × 100, arredondada para o inteiro mais
  próximo (metade para cima). O de maior faturamento fica com 100; quem não tem OS, ou só tem OS cancelada ou sem
  valor, fica com 0; se ninguém faturou ainda, todos ficam com 0.
- Exemplo: A faturou R$ 400.000 e B, R$ 100.000 → A = 100 e B = 25. Se B passa a R$ 200.000 → B = 50.
- O valor **não é digitado nem gravado**: é recalculado a cada consulta, então acompanha sozinho as OS abertas,
  canceladas e alteradas. A coluna `Clientes.prioridade` deixa de ser usada pela aplicação (fica NULL). Se a Fênix
  quiser o valor gravado no banco, o mesmo cálculo pode virar uma view ou procedure na Etapa 3.
- A prioridade da **OS** é uma cópia do valor do cliente no momento em que a OS é aberta; não é recalculada depois.
- Dois pontos que são decisão de implementação, não da Fênix, e valem confirmar: o período é **todo o histórico** (não
  só os últimos 12 meses) e OS não concluída também conta (não só as concluídas). Mudar qualquer um é um ajuste em
  `PrioridadeClienteService.calcularTodas()`.

**Tipos de tinta** (coluna `Tipo` da aba Cadastros da planilha "Controle de Estoque de Tinta - Fênix", 101 tintas, 13
sem tipo). Grafias unificadas: `Hibrida`, `Hibrido` e `Híbrido` → **Híbrido**; `Poliester` → **Poliéster**.
Anodic, Contaminado, Híbrido, Jato, Liso, Poliéster, Poliéster TGIC/BPA FREE, Queima de Estoque, Rio Preto.
"Contaminado", "Jato" e "Queima de Estoque" não são tipos de tinta (são situação ou categoria do estoque), mas estão
em uso na planilha, então entraram; retire de `TIPOS_TINTA` o que a Fênix não quiser.

**Acabamentos de tinta** (coluna `Acabamento`, 53 tintas sem acabamento). Só mudou `Atlantico` → **Atlântico**.
Acetinado, Atlântico, Azulado, Brilhante, Craqueado C/ Cobre Fosco, Econômico, Fosco, Jato, Liso, Liso BR, Liso
Brilhante, Liso Perolizado, Liso Semi Brilho, Liso Semi Fosco, Microtextura, Microtextura Fosco, Semi Brilho,
Text Linea, Texturizado. "Azulado", "Atlântico", "Econômico" e "Jato" parecem cor, linha ou categoria, não acabamento.
A tela de tintas também aceita valores que já estejam gravados e não estejam na lista.

**"Jato" é a etapa Jateamento.** A etapa tem nome fixo no banco (`RotaProcessoService.JATEAMENTO`, criada na inicialização
pela `VerificacaoListasFixas`). Por isso a rota padrão aparece na tela como "Jateamento → Pintura". Para chamá-la de
"Jato", é preciso renomear a constante e a etapa já gravada, o que não foi feito.

**Unidades.** As antigas (UN, PC, KG, M, M2) saíram: `UN`, `PC`, `M` e `M2` passam a ser recusadas. O `application.properties`
é lido em ISO-8859-1, por isso o Ç está escrito como `PE\u00C7A`. O teste `parametrosDefinidosEm22_10` confere o valor.

**Prioridade na tela.** Aparece como número (0 a 100; maior = mais prioritária), não mais como "P1" … "P5". No cadastro de
cliente o campo é só de leitura ("Calculada pelo sistema"); na Nova OS e no diálogo da OS é um campo numérico de 0 a 100.

## 9. Segurança no navegador

- **Login (HTTP Basic, sem sessão no servidor):** a credencial fica no `sessionStorage` da aba (o navegador apaga ao
  fechar a aba) e vai no cabeçalho `Authorization` de cada chamada. Nunca vai para o `localStorage` nem para a URL.
  "Sair" apaga a credencial. 401 leva de volta ao login com "sessão expirada". Em produção, o sistema tem de rodar em
  **HTTPS**, porque o Basic só codifica a senha (base64), não a protege.
- **Content-Security-Policy** (`SecurityConfig.CABECALHO_CSP`): o navegador só executa scripts e estilos que estão em
  arquivos do próprio sistema, e as telas só se conectam à API e ao ViaCEP. Consequências práticas:
  - nada de `<script>` ou `style="..."` dentro do HTML;
  - biblioteca nova vai para `static/vendor`, e não para um CDN;
  - outro serviço externo só funciona se for incluído na CSP.
- **Texto da API sempre como texto:** as telas usam `textContent` (função `el` de `js/ui.js`), nunca `innerHTML` com
  dados. Um nome com `<script>` gravado no cadastro aparece como texto e não roda.
- **Fotos:** exigem login, então a tela baixa cada foto com a credencial e mostra a imagem a partir da memória.

## 10. Estrutura

```
static/
├── *.html                 uma página por tela (todas iguais: carregam o js/paginas/<tela>.js)
├── 404.html
├── css/app.css            sistema visual (cores, cartões, tabelas, formulários, diálogos, layout responsivo)
├── img/                   logo e favicon
├── js/
│   ├── api.js             chamadas à API, erros e 401
│   ├── sessao.js          credencial na aba e retorno ao login
│   ├── layout.js          barra lateral, topo, busca, alertas e bloqueio de telas de admin
│   ├── ui.js              componentes (botões, diálogos, avisos, tabelas, abas, campos...)
│   ├── formato.js         datas, moeda, CPF/CNPJ/CEP/telefone, números
│   ├── config.js          listas fixas e provisórias
│   ├── referencias.js     listas de apoio carregadas uma vez por tela
│   ├── graficos.js        cartões de gráfico (Chart.js)
│   ├── viacep.js          endereço pelo CEP (RN-END-03)
│   ├── icones.js          ícones Lucide usados
│   ├── componentes/       pessoa (endereço, documentos, papéis), item da OS e regras da OS
│   └── paginas/           uma por tela
└── vendor/                Chart.js 4.5.1, Inter 5.2.8 e Lucide 0.545.0, com as licenças
```

## 11. Testes

- `mvnw.cmd test` roda os testes JUnit no H2 (perfil `test`), entre eles:
  - `ApiTest`: CRUD de cores, herança cliente+funcionário, fluxo da OS, fotos, dashboard, perfil e erros em
    português, tudo pela API;
  - `SegurancaTest`: rotas públicas, 401/403, CSP, arquivos do front e página 404.
- `ProjetoextensaoApplicationTests` (o `contextLoads` padrão) usa o banco real: precisa do SQL Server ligado com o
  script aplicado.
- O vídeo da Etapa 2 pode seguir os fluxos testados no navegador: login errado, cadastre-se, lembrete de senha,
  cadastros com herança, OS com itens, produção e retrabalho, operador sem acesso às funções de admin, sessão expirada
  e celular.

## 12. Adaptações em relação ao protótipo do Lovable

| No protótipo | No sistema | Motivo |
|---|---|---|
| Nova OS em diálogo, peças depois | Tela própria, OS e itens juntos | RN-OS-01: a OS só é gravada com pelo menos um item. |
| Produção com "quantidade produzida" e "defeitos" | Fila de etapas da rota, retrabalho e % de retrabalho | O banco registra etapas (ItensRotasProcessos), não quantidades produzidas. |
| Configurações com abas Empresa e Notificações | Listas de apoio, parâmetros e acessos | O banco não guarda dados da empresa nem preferências de notificação. "Conta" virou Meu perfil. |
| Login por "login ou e-mail" | Só login | É o que o back-end autentica. |
| Botão "Novo usuário" | Não existe | O acesso nasce no Cadastre-se do próprio funcionário (RN-USU-02). |
| Prioridade "Alta/Média/Baixa" | Número de 0 a 100 | Definido pela Fênix em 22/10: escala numérica, calculada pelo faturamento do cliente. |

## 13. Pendências

- **Logo:** `img/fenix-logo.png` foi recortado de uma captura da tela do Lovable (160 px). Troque pelo arquivo original
  do logo no projeto do Lovable (pasta `src/assets`), com o mesmo nome.
- **Gráficos e relatórios de produção:** ligar às views da Etapa 3 (seção 7).
- **Valores a confirmar com a Fênix (seção 8):** a 3ª rota padrão ("Jato, Pintura" é igual à 1ª), o período e o critério do faturamento da
  prioridade (todo o histórico, OS não canceladas) e se "Contaminado", "Jato" e "Queima de Estoque" ficam nas listas de tinta.
- **HTTPS** quando o sistema sair do computador de desenvolvimento (seção 9).
