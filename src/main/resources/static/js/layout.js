// Estrutura comum das telas internas (INCLUÍDO em 21/10/2026): barra lateral, barra do topo e área de conteúdo,
// como o __root.tsx, o app-sidebar.tsx e o topbar.tsx do protótipo do Lovable.
//
// Visões por perfil (RN-SIS-02 e RN-SIS-03): o menu, os botões e os avisos mudam conforme o usuário seja ADMIN ou
// OPERADOR. Esconder botões é só conforto: quem garante o acesso é o @PreAuthorize de cada método dos Services, que
// recusa com 403 o que o perfil não pode fazer, mesmo que alguém chame a API direto.

import { get } from './api.js';
import { VERSAO } from './config.js';
import { iniciais, nItem, nOs, diaMes } from './formato.js';
import { credencial, irParaLogin, limparSessao, salvarUsuario } from './sessao.js';
import { anexar, badge, cartao, el, icone, itemMenu, menuSuspenso, mostrarAvisosGuardados, status, trocar } from './ui.js';

const MENU = [
  {
    grupo: 'Operação',
    itens: [
      { id: 'dashboard', titulo: 'Dashboard', url: 'index.html', icone: 'layout-dashboard' },
      { id: 'ordens', titulo: 'Ordens de Serviço', url: 'ordens.html', icone: 'clipboard-list' },
      { id: 'pecas', titulo: 'Peças', url: 'pecas.html', icone: 'boxes' },
      { id: 'producao', titulo: 'Produção', url: 'producao.html', icone: 'factory' },
      { id: 'cronograma', titulo: 'Cronograma', url: 'cronograma.html', icone: 'calendar-days' },
    ],
  },
  {
    grupo: 'Cadastros',
    itens: [
      { id: 'clientes', titulo: 'Clientes', url: 'clientes.html', icone: 'users' },
      { id: 'funcionarios', titulo: 'Funcionários', url: 'funcionarios.html', icone: 'hard-hat' },
      { id: 'fornecedores', titulo: 'Fornecedores', url: 'fornecedores.html', icone: 'truck' },
      { id: 'tintas', titulo: 'Tintas', url: 'tintas.html', icone: 'palette' },
    ],
  },
  {
    grupo: 'Sistema',
    itens: [
      { id: 'relatorios', titulo: 'Relatórios', url: 'relatorios.html', icone: 'chart-column' },
      // Listas de apoio e parâmetros: só ADMIN altera (RN-SIS-03), então a tela nem aparece para o OPERADOR.
      { id: 'configuracoes', titulo: 'Configurações', url: 'configuracoes.html', icone: 'settings', somenteAdmin: true },
    ],
  },
];

const CHAVE_RECOLHIDA = 'fenix.sidebar.recolhida';

function lerPreferencia() {
  try { return localStorage.getItem(CHAVE_RECOLHIDA) === '1'; } catch { return false; }
}

function gravarPreferencia(recolhida) {
  try { localStorage.setItem(CHAVE_RECOLHIDA, recolhida ? '1' : '0'); } catch { /* preferência só desta visita */ }
}

/** Promise que nunca termina: interrompe o script da página enquanto o navegador troca de tela. */
const parar = () => new Promise(() => {});

/**
 * Monta a tela interna e devolve { usuario, admin, conteudo }.
 * Sem login, vai para login.html. somenteAdmin: a tela inteira é do ADMIN (devolve null para o OPERADOR).
 */
export async function montarPagina({ pagina, titulo, somenteAdmin = false }) {
  document.title = `${titulo} — Fênix`;
  const raiz = document.getElementById('app');
  if (!credencial()) {
    irParaLogin();
    return parar();
  }

  let usuario;
  try {
    usuario = await get('/auth/me'); // RN-SIS-01: confere a credencial a cada tela
    salvarUsuario(usuario);
  } catch (e) {
    if (e.status === 401) return parar(); // a api.js já mandou para o login
    mostrarFalha(raiz, e);
    return parar();
  }
  const admin = !!usuario.admin;

  const conteudo = el('main', { class: 'conteudo', id: 'conteudo' });
  const app = el('div', { class: 'app' + (lerPreferencia() ? ' recolhida' : '') },
    barraLateral(pagina, admin),
    el('div', { class: 'sidebar-espaco' }),
    el('div', { class: 'sidebar-sombra', onclick: () => app.classList.remove('menu-aberto') }),
    el('div', { class: 'principal' }, barraTopo(usuario, admin, () => alternarMenu(app)), conteudo));
  trocar(raiz, app);
  raiz.classList.remove('app-carregando');
  mostrarAvisosGuardados();

  if (somenteAdmin && !admin) {
    conteudo.append(cartao({
      classe: 'max-w-md',
      corpo: el('div', { class: 'vazio' }, icone('lock'), el('p', { class: 'seminegrito', text: 'Acesso restrito ao Administrador' }),
        el('p', { class: 'texto-xs', text: 'Esta tela altera as listas de apoio e os parâmetros do sistema (RN-SIS-03).' }),
        el('a', { class: 'btn btn-contorno btn-sm mt-2', href: 'index.html', text: 'Voltar ao início' })),
    }));
    return null;
  }
  return { usuario, admin, conteudo };
}

function alternarMenu(app) {
  if (window.matchMedia('(max-width: 767.98px)').matches) {
    app.classList.toggle('menu-aberto');
  } else {
    const recolhida = app.classList.toggle('recolhida');
    gravarPreferencia(recolhida);
  }
}

function barraLateral(pagina, admin) {
  const marca = el('a', { class: 'sidebar-marca', href: 'index.html', title: 'Fênix' },
    el('div', { class: 'logo-caixa' }, el('img', { src: 'img/fenix-logo.png', alt: 'Fênix', width: 40, height: 40 })),
    el('div', { class: 'marca-texto' }, el('span', { class: 'marca-nome', text: 'FÊNIX' }), el('span', { class: 'marca-sub', text: 'Metalúrgica' })));
  const grupos = MENU.map((g) => {
    const itens = g.itens.filter((i) => admin || !i.somenteAdmin);
    if (!itens.length) return null;
    return el('div', { class: 'sidebar-grupo' },
      el('div', { class: 'sidebar-rotulo', text: g.grupo }),
      el('nav', { class: 'sidebar-menu', 'aria-label': g.grupo }, itens.map((i) => el('a', {
        class: 'sidebar-item' + (i.id === pagina ? ' ativo' : ''), href: i.url, title: i.titulo,
        'aria-current': i.id === pagina ? 'page' : undefined,
      }, icone(i.icone), el('span', { text: i.titulo })))));
  });
  return el('aside', { class: 'sidebar', 'aria-label': 'Menu principal' },
    el('div', { class: 'sidebar-topo' }, marca),
    el('div', { class: 'sidebar-conteudo' }, grupos),
    el('div', { class: 'sidebar-rodape' }, el('div', { class: 'sidebar-versao' },
      el('strong', { text: VERSAO }),
      el('span', { text: admin ? 'Perfil: Administrador' : 'Perfil: Operador' }))));
}

function barraTopo(usuario, admin, aoAlternar) {
  const busca = el('input', {
    class: 'input fundo-mudo', type: 'search', placeholder: 'Buscar OS (nº), peça (#nº) ou cliente...',
    'aria-label': 'Buscar OS, peça ou cliente',
  });
  busca.addEventListener('keydown', (ev) => {
    if (ev.key !== 'Enter') return;
    const termo = busca.value.trim();
    if (!termo) return;
    const peca = /^#\s*(\d+)$/.exec(termo);
    const os = /^(?:os[\s-]*)?(\d+)$/i.exec(termo);
    if (peca) location.href = `peca.html?codigo=${peca[1]}`;
    else if (os) location.href = `ordem.html?numero=${os[1]}`;
    else location.href = `ordens.html?busca=${encodeURIComponent(termo)}`;
  });

  const ponto = el('span', { class: 'ponto-alerta', hidden: true });
  const sino = el('button', { type: 'button', class: 'btn btn-fantasma btn-icone relativo', 'aria-label': 'Notificações', title: 'Notificações' }, icone('bell', 'icone-5'), ponto);
  let alertas = null;
  const carregarAlertas = async () => {
    const [resumo, atrasados] = await Promise.all([get('/dashboard/resumo'), get('/itens/atrasados')]);
    alertas = { resumo, atrasados };
    ponto.hidden = !(atrasados.length || resumo.tintasSemEstoque || resumo.osPorStatus?.['Pausada']);
    return alertas;
  };
  carregarAlertas().catch(() => { /* o sino fica sem o ponto; o resto da tela segue */ });
  const menuSino = menuSuspenso(sino, async () => {
    const itens = [el('div', { class: 'menu-rotulo', text: 'Notificações' }), el('div', { class: 'menu-separador' })];
    try {
      const { resumo, atrasados } = alertas || await carregarAlertas();
      atrasados.slice(0, 4).forEach((i) => itens.push(itemMenu('', () => { location.href = `peca.html?codigo=${i.codigo}`; }, {
        detalhe: el('div', { class: 'largura-total' },
          el('div', { class: 'linha-entre' }, el('span', { class: 'medio', text: `${nOs(i.osNumero)} · item ${nItem(i.codigo)} atrasado` }), status('Atrasada')),
          el('span', { class: 'texto-xs texto-mudo', text: `Prazo ${diaMes(i.prazoEntrega)} · ${i.descricao}` })),
      })));
      if (atrasados.length > 4) itens.push(itemMenu(`Ver os ${atrasados.length} itens atrasados`, () => { location.href = 'pecas.html?atrasadas=1'; }, { nomeIcone: 'arrow-right' }));
      const pausadas = resumo.osPorStatus?.['Pausada'] || 0;
      if (pausadas) itens.push(itemMenu(`${pausadas} OS pausada(s)`, () => { location.href = 'ordens.html?status=Pausada'; }, { nomeIcone: 'pause' }));
      if (resumo.tintasSemEstoque) itens.push(itemMenu(`${resumo.tintasSemEstoque} tinta(s) sem estoque`, () => { location.href = 'tintas.html?semEstoque=1'; }, { nomeIcone: 'palette' }));
      if (itens.length === 2) itens.push(el('div', { class: 'menu-texto', text: 'Nenhum alerta no momento.' }));
    } catch {
      itens.push(el('div', { class: 'menu-texto', text: 'Não foi possível carregar os alertas.' }));
    }
    return itens;
  }, { largo: true });

  const perfilTexto = admin ? 'Administrador' : 'Operador';
  const botaoUsuario = el('button', { type: 'button', class: 'usuario-botao' },
    el('span', { class: 'avatar avatar-8 avatar-gradiente', text: iniciais(usuario.nome) }),
    el('span', { class: 'usuario-texto' }, el('p', { class: 'usuario-nome', text: usuario.nome }), el('p', { class: 'usuario-perfil', text: perfilTexto })));
  const menuUsuario = menuSuspenso(botaoUsuario, () => [
    el('div', { class: 'menu-rotulo', text: 'Minha conta' }),
    el('div', { class: 'menu-separador' }),
    el('div', { class: 'menu-texto' }, 'Nível de acesso atual: ',
      admin ? badge('Administrador', 'primario', 'shield-check') : badge('Operador', 'contorno', 'user')),
    el('div', { class: 'menu-texto', text: `Login: ${usuario.login}` }),
    el('div', { class: 'menu-separador' }),
    itemMenu('Meu perfil', () => { location.href = 'perfil.html'; }, { nomeIcone: 'user-cog' }),
    admin ? itemMenu('Configurações', () => { location.href = 'configuracoes.html'; }, { nomeIcone: 'settings' }) : null,
    el('div', { class: 'menu-separador' }),
    itemMenu('Sair', sair, { nomeIcone: 'log-out', perigo: true }),
  ].filter(Boolean));

  return el('header', { class: 'topo' },
    el('button', { type: 'button', class: 'btn btn-fantasma btn-icone-sm texto-mudo', 'aria-label': 'Mostrar ou recolher o menu', title: 'Menu', onclick: aoAlternar }, icone('panel-left')),
    el('div', { class: 'logo-caixa logo-9 so-celular' }, el('img', { src: 'img/fenix-logo.png', alt: 'Fênix', width: 36, height: 36 })),
    el('div', { class: 'topo-busca com-icone' }, icone('search'), busca),
    el('div', { class: 'topo-acoes' }, menuSino, menuUsuario));
}

/** Logout: apaga a credencial do navegador (o servidor não guarda sessão). */
export function sair() {
  limparSessao();
  location.replace('login.html?motivo=saiu');
}

function mostrarFalha(raiz, e) {
  raiz.classList.remove('app-carregando');
  trocar(raiz, el('div', { class: 'tela-centro' }, el('div', { class: 'login-caixa' },
    cartao({
      corpo: [
        el('div', { class: 'vazio' }, icone('circle-alert'), el('p', { class: 'seminegrito', text: 'Não foi possível abrir o sistema' }),
          el('p', { class: 'texto-xs', text: (e?.mensagens || [e?.message]).join(' ') })),
        el('div', { class: 'linha' },
          el('button', { type: 'button', class: 'btn btn-primario btn-bloco', onclick: () => location.reload() }, icone('refresh-cw'), el('span', { text: 'Tentar novamente' }))),
      ],
    }))));
}

export { anexar };
