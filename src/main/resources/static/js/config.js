// Listas e valores fixos do front-end (INCLUÍDO em 21/10/2026).
//
// O que o back-end já fornece vem da API (GET /api/parametros: escala de prioridade, unidades do item, cargos,
// prazos de pagamento, limite de fotos e de senha). Aqui ficam só:
//  - o que a tela de login precisa antes de haver login (a API exige login para /api/parametros);
//  - as listas que a planilha de regras marca como "Lista fixa no front". Os valores abaixo foram definidos pela
//    Fênix em 22/10/2026 (tipos e acabamentos: valores usados hoje na planilha de estoque de tinta).

/** Versão mostrada no rodapé da barra lateral. */
export const VERSAO = 'v1.0 · Produção';

/** Perguntas de segurança oferecidas no cadastre-se e no perfil (as mesmas do protótipo do Lovable). */
export const PERGUNTAS_SEGURANCA = [
  'Qual o nome do seu primeiro animal de estimação?',
  'Qual o nome da cidade onde você nasceu?',
  'Qual o nome da sua mãe?',
  'Qual foi sua primeira escola?',
  'Qual seu time de futebol?',
];

/** RN-USU-15: espelho de UsuarioService.SENHA_MINIMO e SENHA_MAXIMO (usado antes do login). */
export const SENHA = { minimo: 8, maximo: 72 };

/**
 * RN-TIN-08 — tipos de tinta usados hoje na planilha "Controle de Estoque de Tinta - Fênix" (aba Cadastros, coluna
 * Tipo), com as grafias Hibrido/Hibrida/Híbrido unificadas em "Híbrido" e "Poliester" escrito "Poliéster".
 * "Contaminado", "Jato" e "Queima de Estoque" não são tipos de tinta, mas estão no estoque; retire daqui se a
 * Fênix não quiser vê-los na lista.
 */
export const TIPOS_TINTA = [
  'Anodic', 'Contaminado', 'Híbrido', 'Jato', 'Liso', 'Poliéster', 'Poliéster TGIC/BPA FREE', 'Queima de Estoque', 'Rio Preto',
];

/**
 * RN-TIN-10 — acabamentos usados hoje na mesma planilha (coluna Acabamento), sem alteração de grafia, exceto
 * "Atlantico" -> "Atlântico". "Azulado", "Atlântico", "Econômico" e "Jato" parecem cor, linha ou categoria, não
 * acabamento; ficam porque estão em uso.
 */
export const ACABAMENTOS_TINTA = [
  'Acetinado', 'Atlântico', 'Azulado', 'Brilhante', 'Craqueado C/ Cobre Fosco', 'Econômico', 'Fosco', 'Jato', 'Liso',
  'Liso BR', 'Liso Brilhante', 'Liso Perolizado', 'Liso Semi Brilho', 'Liso Semi Fosco', 'Microtextura',
  'Microtextura Fosco', 'Semi Brilho', 'Text Linea', 'Texturizado',
];

/**
 * RN-ROT-01 — rotas padrão sugeridas ao abrir o item (definidas pela Fênix em 22/10/2026: "Jato -> Pintura;
 * Banho -> Pintura; Jato, Pintura"). "Jato" é a etapa Jateamento, cujo nome é fixo no banco (RotaProcessoService.
 * JATEAMENTO, criada na inicialização). A terceira rota é igual à primeira e não foi repetida (a confirmar com a
 * Fênix). A tela só mostra a sugestão quando todas as etapas existem no cadastro (Configurações), com estes nomes.
 */
export const ROTAS_PADRAO = [
  ['Jateamento', 'Pintura'],
  ['Banho', 'Pintura'],
];

/** Nome da etapa que exige cor e tinta no item (RotaProcessoService.PINTURA, RN-ITE-05). */
export const ETAPA_PINTURA = 'Pintura';

/** Unidades federativas (RN-END-05). */
export const UFS = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE',
  'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO'];

/**
 * Gráficos do painel inicial. Os dados vêm de GET /api/dashboard/graficos/{nome}; enquanto o back-end responder
 * "disponivel: false" (dados ainda não programados — ver DashboardService), a tela mostra o aviso no lugar do
 * gráfico. Quando a consulta for feita (ex.: com as views da Etapa 3), o gráfico aparece sem mudar o front.
 */
export const GRAFICOS_PAINEL = {
  producaoDiaria: { nome: 'producao-diaria', titulo: 'Produção diária', descricao: 'Etapas concluídas por dia' },
  retrabalhoMensal: { nome: 'retrabalho-mensal', titulo: 'Retrabalho mensal', descricao: '% de itens com etapa repetida (indicador do PDCA)' },
  etapasPorFuncionario: { nome: 'etapas-por-funcionario', titulo: 'Produtividade por funcionário', descricao: 'Etapas concluídas por operador' },
  osPorStatus: { nome: 'os-por-status', titulo: 'OS por status', descricao: 'Distribuição das ordens de serviço' },
};
