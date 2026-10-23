// Componentes de tela (INCLUÍDO em 21/10/2026): equivalentes, em JavaScript puro, aos componentes do protótipo
// do Lovable (PageHeader, StatCard, StatusBadge, PriorityBadge, AccessNotice, Dialog, Tabs, Toaster...).
//
// Segurança: todo texto vindo da API entra na página como texto (textContent), nunca como HTML. Assim um nome
// de cliente com "<script>" aparece escrito, sem ser executado (e a CSP do SecurityConfig bloqueia script embutido).

import { icone } from './icones.js';

// ------------------------------------------------------------------ criação de elementos

const PROPRIEDADES_BOOLEANAS = new Set(['checked', 'disabled', 'readOnly', 'selected', 'required', 'multiple', 'hidden', 'open']);

/** el('div', { class: 'card', onclick: fn }, filhos...) */
export function el(tag, props = {}, ...filhos) {
  const e = document.createElement(tag);
  let valor;
  for (const [k, v] of Object.entries(props || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'text') e.textContent = v;
    else if (k === 'value') valor = v;
    else if (k === 'dataset') Object.assign(e.dataset, v);
    else if (k.startsWith('on') && typeof v === 'function') e.addEventListener(k.slice(2), v);
    else if (PROPRIEDADES_BOOLEANAS.has(k)) e[k] = true;
    else e.setAttribute(k, v === true ? '' : String(v));
  }
  anexar(e, filhos);
  if (valor !== undefined) e.value = valor; // depois dos <option>, para o <select> aceitar o valor
  return e;
}

export function anexar(pai, filhos) {
  for (const f of [filhos].flat(Infinity)) {
    if (f === null || f === undefined || f === false || f === '') continue;
    pai.append(f instanceof Node ? f : document.createTextNode(String(f)));
  }
  return pai;
}

export function limpar(e) {
  while (e.firstChild) e.firstChild.remove();
  return e;
}

/** Troca o conteúdo do elemento. */
export function trocar(e, ...filhos) {
  limpar(e);
  return anexar(e, filhos);
}

export { icone };

// ------------------------------------------------------------------ botões

/** botao('Salvar', { variante: 'primario', icone: 'save', onclick }) */
export function botao(texto, { variante = 'contorno', tamanho, icone: nomeIcone, classe = '', titulo, tipo = 'button', ...resto } = {}) {
  const classes = ['btn', `btn-${variante}`];
  if (tamanho) classes.push(`btn-${tamanho}`);
  if (classe) classes.push(classe);
  const b = el('button', { type: tipo, class: classes.join(' '), title: titulo, 'aria-label': texto ? undefined : titulo, ...resto });
  if (nomeIcone) b.append(icone(nomeIcone));
  if (texto) b.append(el('span', { text: texto }));
  return b;
}

/** Botão só com ícone (ghost, 32 px), com dica. */
export function botaoIcone(nomeIcone, titulo, onclick, { variante = 'fantasma', tamanho = 'icone-sm', ...resto } = {}) {
  return botao('', { variante, tamanho, icone: nomeIcone, titulo, onclick, ...resto });
}

/** Desliga o botão e mostra o ícone girando enquanto a ação roda. */
export async function ocupado(botaoEl, acao) {
  if (!botaoEl) return acao();
  if (botaoEl.classList.contains('carregando')) return undefined;
  const iconeOriginal = botaoEl.querySelector('.icone');
  const giro = icone('loader-circle', 'icone-giro');
  if (iconeOriginal) iconeOriginal.replaceWith(giro); else botaoEl.prepend(giro);
  botaoEl.classList.add('carregando');
  botaoEl.disabled = true;
  try {
    return await acao();
  } finally {
    botaoEl.classList.remove('carregando');
    botaoEl.disabled = false;
    if (iconeOriginal) giro.replaceWith(iconeOriginal); else giro.remove();
  }
}

// ------------------------------------------------------------------ avisos flutuantes (Sonner)

let pilhaAvisos;
const ICONE_AVISO = { sucesso: 'circle-check', erro: 'circle-x', alerta: 'triangle-alert', info: 'info' };

/** aviso('Cliente salvo.', { tipo: 'sucesso' | 'erro' | 'alerta' | 'info', detalhe, lista }) */
export function aviso(texto, { tipo = 'sucesso', detalhe, lista, duracao } = {}) {
  if (!pilhaAvisos || !pilhaAvisos.isConnected) {
    pilhaAvisos = el('div', { class: 'avisos', role: 'status', 'aria-live': 'polite' });
    document.body.append(pilhaAvisos);
  }
  const corpo = el('div', { class: 'aviso-texto' }, el('strong', { text: texto }));
  if (detalhe) corpo.append(el('span', { text: detalhe }));
  if (lista?.length) corpo.append(el('ul', {}, lista.map((m) => el('li', { text: m }))));
  const caixa = el('div', { class: `aviso aviso-${tipo}` }, icone(ICONE_AVISO[tipo] || 'info'), corpo);
  const fechar = () => {
    caixa.classList.add('saindo');
    setTimeout(() => caixa.remove(), 200);
  };
  caixa.addEventListener('click', fechar);
  pilhaAvisos.append(caixa);
  // Como o Sonner do protótipo: no máximo 3 avisos na tela; o mais antigo sai primeiro.
  const visiveis = [...pilhaAvisos.querySelectorAll('.aviso:not(.saindo)')];
  if (visiveis.length > 3) visiveis.slice(0, visiveis.length - 3).forEach((a) => a.click());
  setTimeout(fechar, duracao ?? (tipo === 'erro' ? 7000 : 4500));
  return caixa;
}

export const sucesso = (texto, op) => aviso(texto, { ...op, tipo: 'sucesso' });
export const alerta = (texto, op) => aviso(texto, { ...op, tipo: 'alerta' });
export const informar = (texto, op) => aviso(texto, { ...op, tipo: 'info' });

/** Mostra o erro da API (uma ou várias mensagens da planilha de regras). */
export function mostrarErro(e, titulo = 'Não foi possível concluir:') {
  const mensagens = e?.mensagens || [e?.message || 'Erro inesperado.'];
  if (e?.status === 401) return null; // já voltou para o login
  if (mensagens.length === 1) return aviso(mensagens[0], { tipo: 'erro' });
  return aviso(titulo, { tipo: 'erro', lista: mensagens });
}

/** Mostra os avisos que a API devolve junto com o registro (ex.: "tinta sem estoque", "nome parecido"). */
export function mostrarAvisos(avisos) {
  (avisos || []).forEach((a) => aviso(a, { tipo: 'alerta', duracao: 7000 }));
}

const CHAVE_AVISOS = 'fenix.avisos';

/** Guarda um aviso para mostrar na próxima tela (ex.: "OS aberta" antes de ir para a tela da OS). */
export function avisarNaProximaTela(texto, opcoes = {}) {
  try {
    const lista = JSON.parse(sessionStorage.getItem(CHAVE_AVISOS) || '[]');
    lista.push({ texto, opcoes });
    sessionStorage.setItem(CHAVE_AVISOS, JSON.stringify(lista));
  } catch {
    /* sem armazenamento: o aviso se perde, a operação já foi gravada */
  }
}

export function mostrarAvisosGuardados() {
  try {
    const lista = JSON.parse(sessionStorage.getItem(CHAVE_AVISOS) || '[]');
    sessionStorage.removeItem(CHAVE_AVISOS);
    lista.forEach((a) => aviso(a.texto, a.opcoes));
  } catch {
    /* nada guardado */
  }
}

// ------------------------------------------------------------------ caixas de mensagem dentro da tela

export function caixaErro(mensagens) {
  const lista = [mensagens].flat().filter(Boolean);
  const conteudo = lista.length === 1 ? el('div', { text: lista[0] }) : el('div', {}, el('strong', { text: 'Corrija os dados:' }), el('ul', {}, lista.map((m) => el('li', { text: m }))));
  return el('div', { class: 'alerta alerta-erro', role: 'alert' }, icone('circle-alert'), conteudo);
}

export function caixaInfo(texto, { tipo = 'info', nomeIcone = 'info' } = {}) {
  return el('div', { class: `alerta alerta-${tipo}` }, icone(nomeIcone), el('div', {}, texto));
}

// ------------------------------------------------------------------ diálogo (Dialog)

let contadorDialogo = 0;

/**
 * Abre um <dialog> modal. Devolve { elemento, corpo, rodape, fechar, mostrarErro, limparErro }.
 * largura: 'xl' | '2xl' | '3xl' | '4xl' (padrão 32rem, como o sm:max-w-lg do protótipo).
 */
export function dialogo({ titulo, descricao, conteudo, botoes = [], largura, aoFechar, classe = '' } = {}) {
  const id = `dialogo-${++contadorDialogo}`;
  const d = el('dialog', { class: `dialogo ${largura ? 'largura-' + largura : ''} ${classe}`.trim(), 'aria-labelledby': id + '-titulo' });
  const cab = el('div', { class: 'dialogo-cab' }, el('h2', { class: 'dialogo-titulo', id: id + '-titulo', text: titulo }));
  if (descricao) cab.append(el('p', { class: 'dialogo-desc', text: descricao }));
  const areaErro = el('div', { class: 'mb-4', hidden: true });
  const corpo = el('div', { class: 'dialogo-corpo' }, areaErro);
  anexar(corpo, conteudo);
  const rodape = el('div', { class: 'dialogo-rodape' });
  anexar(rodape, botoes);
  const fecharX = el('button', { type: 'button', class: 'dialogo-fechar', 'aria-label': 'Fechar janela', title: 'Fechar janela', onclick: () => d.close() }, icone('x'));
  d.append(cab, corpo, botoes.length ? rodape : null, fecharX);
  d.addEventListener('mousedown', (ev) => { if (ev.target === d) d.close(); });
  d.addEventListener('close', () => {
    d.remove();
    aoFechar?.();
  });
  document.body.append(d);
  d.showModal();
  return {
    elemento: d,
    corpo,
    rodape,
    fechar: () => d.close(),
    mostrarErro(e) {
      const mensagens = e?.mensagens || [e?.message || String(e)];
      trocar(areaErro, caixaErro(mensagens));
      areaErro.hidden = false;
      corpo.scrollTop = 0;
    },
    limparErro() {
      limpar(areaErro);
      areaErro.hidden = true;
    },
  };
}

/** Confirmação (substitui o window.confirm). Devolve Promise<boolean>. */
export function confirmar(mensagem, { titulo = 'Confirmar', textoConfirmar = 'Confirmar', perigo = false, detalhe } = {}) {
  return new Promise((resolver) => {
    let resposta = false;
    const sim = botao(textoConfirmar, { variante: perigo ? 'perigo' : 'primario', onclick: () => { resposta = true; dlg.fechar(); } });
    const nao = botao('Cancelar', { onclick: () => dlg.fechar() });
    const dlg = dialogo({
      titulo,
      conteudo: [el('p', { class: 'texto-sm quebra', text: mensagem }), detalhe ? el('p', { class: 'texto-xs texto-mudo mt-2 quebra', text: detalhe }) : null],
      botoes: [nao, sim],
      aoFechar: () => resolver(resposta),
    });
    sim.focus();
  });
}

/** Pede um texto (substitui o window.prompt). Devolve Promise<string|null>. */
export function pedirTexto({ titulo, rotulo, valor = '', dica, textoConfirmar = 'Salvar', tipo = 'text', mascara, validar } = {}) {
  return new Promise((resolver) => {
    let resposta = null;
    const entrada = el('input', { class: 'input', type: tipo, value: valor });
    if (mascara) mascara(entrada);
    const ok = botao(textoConfirmar, { variante: 'primario', tipo: 'submit' });
    const form = el('form', { novalidate: true }, campo(rotulo, entrada, { dica }));
    const dlg = dialogo({ titulo, conteudo: form, botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), ok], aoFechar: () => resolver(resposta) });
    const enviar = (ev) => {
      ev.preventDefault();
      const erro = validar?.(entrada.value);
      if (erro) { dlg.mostrarErro({ mensagens: [erro] }); return; }
      resposta = entrada.value;
      dlg.fechar();
    };
    form.addEventListener('submit', enviar);
    ok.addEventListener('click', enviar);
    entrada.focus();
  });
}

// ------------------------------------------------------------------ abas (Tabs)

/**
 * abas([{ id, rotulo, icone, conteudo: Node | () => Node }], { ativa, aoTrocar, larguraTotal })
 * Devolve { elemento, ativar(id), painel(id) }. O conteúdo de função é montado só quando a aba abre.
 */
export function abas(lista, { ativa, aoTrocar, larguraTotal = false } = {}) {
  const barra = el('div', { class: 'abas-lista' + (larguraTotal ? ' largura-total' : ''), role: 'tablist' });
  const paineis = {};
  const botoesAba = {};
  const montados = new Set();
  const raiz = el('div', {}, barra);
  const ativar = (id) => {
    Object.entries(botoesAba).forEach(([chave, b]) => {
      const sel = chave === id;
      b.classList.toggle('ativa', sel);
      b.setAttribute('aria-selected', String(sel));
      paineis[chave].hidden = !sel;
    });
    const item = lista.find((a) => a.id === id);
    if (item && typeof item.conteudo === 'function' && !montados.has(id)) {
      montados.add(id);
      anexar(paineis[id], item.conteudo());
    }
    aoTrocar?.(id);
  };
  lista.forEach((a) => {
    const b = el('button', { type: 'button', class: 'aba', role: 'tab', onclick: () => ativar(a.id) });
    if (a.icone) b.append(icone(a.icone));
    b.append(el('span', { text: a.rotulo }));
    botoesAba[a.id] = b;
    barra.append(b);
    const p = el('div', { class: 'aba-painel', role: 'tabpanel', hidden: true });
    if (typeof a.conteudo !== 'function') anexar(p, a.conteudo);
    paineis[a.id] = p;
    raiz.append(p);
  });
  ativar(ativa || lista[0]?.id);
  return {
    elemento: raiz,
    ativar,
    painel: (id) => paineis[id],
    rotular(id, texto) { const s = botoesAba[id]?.querySelector('span'); if (s) s.textContent = texto; },
  };
}

// ------------------------------------------------------------------ menu suspenso (DropdownMenu)

let menuAberto = null;
document.addEventListener('mousedown', (ev) => {
  if (menuAberto && !menuAberto.ancora.contains(ev.target)) fecharMenu();
});
document.addEventListener('keydown', (ev) => {
  if (ev.key === 'Escape' && menuAberto) fecharMenu();
});

function fecharMenu() {
  if (!menuAberto) return;
  menuAberto.painel.remove();
  menuAberto.gatilho.setAttribute('aria-expanded', 'false');
  menuAberto = null;
}

/** Liga um menu ao botão. montar() devolve os itens (Nodes) — chamado a cada abertura. */
export function menuSuspenso(gatilho, montar, { largo = false } = {}) {
  const ancora = el('div', { class: 'menu-ancora' }, gatilho);
  gatilho.setAttribute('aria-haspopup', 'menu');
  gatilho.setAttribute('aria-expanded', 'false');
  gatilho.addEventListener('click', async () => {
    if (menuAberto?.gatilho === gatilho) { fecharMenu(); return; }
    fecharMenu();
    const painel = el('div', { class: 'menu-suspenso' + (largo ? ' largo' : ''), role: 'menu' });
    menuAberto = { ancora, painel, gatilho };
    gatilho.setAttribute('aria-expanded', 'true');
    ancora.append(painel);
    anexar(painel, await montar(fecharMenu));
  });
  return ancora;
}

export function itemMenu(texto, acao, { nomeIcone, perigo = false, detalhe } = {}) {
  const b = el('button', { type: 'button', class: 'menu-item' + (perigo ? ' perigo' : '') + (detalhe ? ' empilhado' : ''), role: 'menuitem' });
  if (detalhe) {
    b.append(detalhe);
  } else {
    if (nomeIcone) b.append(icone(nomeIcone));
    b.append(el('span', { text: texto }));
  }
  b.addEventListener('click', () => { fecharMenu(); acao?.(); });
  return b;
}

// ------------------------------------------------------------------ peças do protótipo

/** PageHeader: título, descrição e ações. */
export function cabecalho(titulo, descricao, acoes = []) {
  const texto = el('div', {}, el('h1', { text: titulo }));
  if (descricao) texto.append(el('p', { text: descricao }));
  const cab = el('div', { class: 'cabecalho' }, texto);
  const lista = [acoes].flat().filter(Boolean);
  if (lista.length) cab.append(el('div', { class: 'cabecalho-acoes' }, lista));
  return cab;
}

/** Link "Voltar". */
export function voltar(texto, destino) {
  return el('a', { class: 'btn btn-fantasma btn-sm voltar', href: destino }, icone('arrow-left'), el('span', { text: texto }));
}

/** StatCard: destaque 'primary' | 'success' | 'warning' | 'info' | 'destructive'. */
export function indicador({ titulo, valor, dica, nomeIcone, destaque = 'primary' }) {
  const valorEl = el('p', { class: 'indicador-valor', text: valor ?? '—' });
  const dicaEl = el('p', { class: 'indicador-dica', text: dica || '', hidden: !dica });
  const card = el('div', { class: `card indicador destaque-${destaque}` },
    el('div', { class: 'indicador-topo' },
      el('div', {}, el('p', { class: 'indicador-titulo', text: titulo }), valorEl, dicaEl),
      el('div', { class: 'indicador-icone' }, icone(nomeIcone))));
  card.definir = (v, d) => {
    valorEl.textContent = v ?? '—';
    if (d !== undefined) { dicaEl.textContent = d || ''; dicaEl.hidden = !d; }
  };
  return card;
}

/** Card com cabeçalho (título, descrição, ações à direita) e corpo. */
export function cartao({ titulo, descricao, acoes, corpo, classe = '', tituloSecao = false, semCorpo = false } = {}) {
  const c = el('div', { class: `card ${classe}`.trim() });
  if (titulo || descricao) {
    const tit = el('div', { class: 'coluna' });
    if (titulo) tit.append(el('h3', { class: 'card-titulo' + (tituloSecao ? ' secao' : ''), text: titulo }));
    if (descricao) tit.append(el('p', { class: 'card-desc mt-1', text: descricao }));
    const cab = el('div', { class: 'card-cab' + (acoes ? ' em-linha' : '') }, tit);
    if (acoes) cab.append(el('div', { class: 'linha' }, acoes));
    c.append(cab);
  }
  const corpoEl = semCorpo ? c : el('div', { class: titulo || descricao ? 'card-corpo' : 'card-p6' });
  if (!semCorpo) c.append(corpoEl);
  anexar(corpoEl, corpo);
  c.corpo = corpoEl;
  return c;
}

/** AccessNotice com o texto do perfil. */
export function avisoAcesso(admin, textoOperador = 'Perfil Operador — você cadastra e altera; excluir, inativar e corrigir documentos é com o Administrador.') {
  return admin
    ? el('div', { class: 'aviso-acesso admin' }, icone('shield-check'), el('span', { text: 'Modo Administrador — edição liberada.' }))
    : el('div', { class: 'aviso-acesso operador' }, icone('lock'), el('span', { text: textoOperador }));
}

/** Badge simples. variante: primario | sucesso | alerta | info | perigo | mudo | contorno */
export function badge(texto, variante = 'contorno', nomeIcone) {
  const b = el('span', { class: `badge badge-${variante}` });
  if (nomeIcone) b.append(icone(nomeIcone));
  b.append(document.createTextNode(texto));
  return b;
}

// Status da OS (StatusOsEnum) e do item (StatusItemEnum) no visual do StatusBadge do protótipo.
const CLASSE_STATUS = {
  'Criada': 'aberta',
  'Em produção': 'andamento',
  'Pausada': 'pausada',
  'Concluída': 'concluida',
  'Cancelada': 'cancelada',
  'Pendente': 'pendente',
  'Aguardando execução': 'aberta',
  'Executada': 'concluida',
  'Atrasada': 'atrasada',
  'Retrabalho': 'retrabalho',
  'Em execução': 'andamento',
  'Pronta para iniciar': 'aberta',
};

export function status(texto) {
  return el('span', { class: `status status-${CLASSE_STATUS[texto] || 'pausada'}`, text: texto || '—' });
}

/** Prioridade de 0 a 100 (RN-CLI-09, definida em 22/10/2026): maior número = mais prioritária. */
export function prioridade(valor) {
  if (valor === null || valor === undefined) return el('span', { class: 'prioridade vazia', text: 'Sem prioridade', title: 'Prioridade não informada' });
  return el('span', { class: 'prioridade', text: String(valor), title: `Prioridade ${valor} de 100 (maior número = mais prioritária)` });
}

/** Estado vazio (lista sem registros). */
export function vazio(texto, nomeIcone = 'package') {
  return el('div', { class: 'vazio' }, icone(nomeIcone), el('p', { text: texto }));
}

/** Esqueleto de carregamento. */
export function esqueleto(linhas = 3, classe = 'esqueleto-linha') {
  return el('div', { class: 'pilha-2', 'aria-busy': 'true' }, Array.from({ length: linhas }, () => el('div', { class: `esqueleto ${classe}` })));
}

/** Bloco "ícone + rótulo + valor" (Info do protótipo). */
export function info(nomeIcone, rotulo, valor, nota) {
  const v = valor instanceof Node ? valor : el('span', { text: valor ?? '—' });
  return el('div', { class: 'info' }, nomeIcone ? icone(nomeIcone) : null,
    el('div', { class: 'flex-1' }, el('p', { class: 'info-rotulo', text: rotulo }), el('div', { class: 'info-valor' }, v),
      nota ? el('p', { class: 'info-nota', text: nota }) : null));
}

// ------------------------------------------------------------------ campos de formulário

/** Campo com rótulo, entrada e dica. Classe de largura na grade de 6 colunas: c1..c6. */
export function campo(rotulo, entrada, { dica, obrigatorio = false, largura, nota } = {}) {
  const id = entrada.id || `campo-${Math.random().toString(36).slice(2, 9)}`;
  entrada.id = id;
  const lab = el('label', { class: 'rotulo', for: id, text: rotulo });
  if (obrigatorio) lab.append(el('span', { class: 'obrigatorio', text: '*', 'aria-hidden': 'true' }));
  if (nota) lab.append(' ', el('span', { class: 'nota', text: nota }));
  const c = el('div', { class: `campo ${largura || ''}`.trim() }, lab, entrada);
  if (dica) c.append(el('p', { class: 'dica', text: dica }));
  return c;
}

export function entrada(props = {}) {
  return el('input', { class: 'input', type: 'text', ...props });
}

export function areaTexto(props = {}) {
  return el('textarea', { class: 'textarea', rows: 3, ...props });
}

/**
 * <select> com opções. lista: [{ valor, rotulo, desabilitado }] ou strings.
 * vazio: texto da primeira opção sem valor (placeholder).
 */
export function selecao(lista, { valor, vazio: textoVazio, ...props } = {}) {
  const s = el('select', { class: 'select', ...props });
  preencherSelecao(s, lista, { valor, vazio: textoVazio });
  return s;
}

export function preencherSelecao(s, lista, { valor, vazio: textoVazio } = {}) {
  limpar(s);
  if (textoVazio !== undefined) s.append(el('option', { value: '', text: textoVazio }));
  lista.forEach((o) => {
    const op = typeof o === 'object' ? o : { valor: o, rotulo: o };
    s.append(el('option', { value: String(op.valor), text: op.rotulo, disabled: op.desabilitado }));
  });
  if (valor !== undefined && valor !== null) s.value = String(valor);
  return s;
}

/** Interruptor (Switch) com texto. */
export function interruptor(texto, { marcado = false, aoMudar } = {}) {
  const caixa = el('input', { type: 'checkbox', checked: marcado, role: 'switch' });
  if (aoMudar) caixa.addEventListener('change', () => aoMudar(caixa.checked));
  const rotulo = el('label', { class: 'interruptor' }, caixa, el('span', { text: texto }));
  rotulo.caixa = caixa;
  return rotulo;
}

/** Campo de busca com lupa. */
export function campoBusca(placeholder, aoDigitar, { valor = '', classe = '' } = {}) {
  const e = el('input', { class: 'input fundo-mudo', type: 'search', placeholder, value: valor, 'aria-label': placeholder });
  let espera;
  e.addEventListener('input', () => { clearTimeout(espera); espera = setTimeout(() => aoDigitar(e.value), 150); });
  const caixa = el('div', { class: `com-icone ${classe}`.trim() }, icone('search'), e);
  caixa.entrada = e;
  return caixa;
}

// ------------------------------------------------------------------ tabela (Table)

/**
 * tabela({ colunas: [{ titulo, classe }], linhas: [...], montar: (registro) => [células], aoClicar, vazio })
 * Cada célula é Node, texto ou { conteudo, classe }.
 */
export function tabela({ colunas, linhas, montar, aoClicar, textoVazio = 'Nenhum registro encontrado.', classeLinha }) {
  const cab = el('thead', {}, el('tr', {}, colunas.map((c) => el('th', { class: c.classe, text: c.titulo ?? '' }))));
  const corpo = el('tbody');
  if (!linhas.length) {
    corpo.append(el('tr', { class: 'vazio-tabela' }, el('td', { colspan: colunas.length, text: textoVazio })));
  }
  linhas.forEach((reg) => {
    const tr = el('tr', { class: [aoClicar ? 'clicavel' : '', classeLinha?.(reg) || ''].join(' ').trim() || undefined });
    montar(reg).forEach((cel, i) => {
      const def = cel && typeof cel === 'object' && !(cel instanceof Node) && 'conteudo' in cel ? cel : { conteudo: cel };
      const td = el('td', { class: [colunas[i]?.classe, def.classe].filter(Boolean).join(' ') || undefined });
      anexar(td, def.conteudo);
      tr.append(td);
    });
    if (aoClicar) {
      tr.addEventListener('click', (ev) => {
        if (ev.target.closest('button, a, input, select, label')) return;
        aoClicar(reg);
      });
    }
    corpo.append(tr);
  });
  return el('div', { class: 'tabela-rolagem' }, el('table', { class: 'tabela' }, cab, corpo));
}

// ------------------------------------------------------------------ utilidades

export function parametroUrl(nome) {
  return new URLSearchParams(location.search).get(nome);
}

/** Baixa um texto como arquivo (relatórios CSV). */
export function baixarArquivo(nome, conteudo, tipo = 'text/csv;charset=utf-8') {
  const blob = new Blob([conteudo], { type: tipo });
  const url = URL.createObjectURL(blob);
  const a = el('a', { href: url, download: nome, hidden: true });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
