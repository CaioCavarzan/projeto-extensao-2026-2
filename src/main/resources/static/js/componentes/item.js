// Formulário do item da OS — "peça" na tela (INCLUÍDO em 21/10/2026).
// Usado em: Nova OS, "Adicionar item" na OS, "Nova peça" e "Editar" na tela da peça.
//
// Regras tratadas aqui para ajudar quem digita (o back-end confere todas de novo):
//  RN-ITE-03  pelo menos uma etapa na rota (Jateamento, Banho, Pintura...), na ordem escolhida;
//  RN-ROT-01  rotas padrão sugeridas (lista definida em 22/10, em config.js);
//  RN-ITE-05  com Pintura na rota, a cor pedida é obrigatória; sem Pintura, o item não leva cor nem tinta;
//  RN-ITE-04  a tinta tem de ser da cor pedida (a lista de tintas é filtrada pela cor);
//  RN-ITE-12  aviso quando a tinta escolhida está sem estoque;
//  RN-ITE-17  unidade escolhida da lista fechada (GET /api/parametros);
//  RN-ITE-24 e RN-OS-15  valor opcional: sem valor, a OS fica com "valor incompleto".

import { ETAPA_PINTURA, ROTAS_PADRAO } from '../config.js';
import { decimalParaCampo, hojeISO, lerDecimal, moeda, normalizar, numero, textoOuNulo } from '../formato.js';
import { botao, campo, el, entrada, icone, preencherSelecao, selecao, trocar } from '../ui.js';

const ehPintura = (nome) => normalizar(nome) === normalizar(ETAPA_PINTURA);

/**
 * formularioItem({ referencias: { etapas, cores, tintas, parametros }, valor, modo: 'novo' | 'edicao', titulo, aoRemover })
 * Devolve { elemento, valor(), erros() }.
 */
export function formularioItem({ referencias, valor = {}, modo = 'novo', titulo, aoRemover } = {}) {
  const { etapas = [], cores = [], tintas = [], parametros } = referencias;
  const v = valor || {};
  const novo = modo === 'novo';

  const descricao = entrada({ value: v.descricao ?? '', maxlength: 100, placeholder: 'Ex.: Portão 2 folhas 3x2 m' });
  const qtd = entrada({ value: decimalParaCampo(v.qtdTotal), inputmode: 'decimal', placeholder: '0' });
  const unidade = selecao(parametros?.unidades || [], { valor: v.unidade ?? '', vazio: 'Unidade' });
  const prazo = entrada({ type: 'date', value: v.prazoEntrega ?? '', min: novo ? hojeISO() : undefined });
  const valorItem = entrada({ value: decimalParaCampo(v.valorTotalItem), inputmode: 'decimal', placeholder: '0,00' });
  const cor = selecao(cores.map((c) => ({ valor: c.codigo, rotulo: c.nome })), { valor: v.corCodigo ?? '', vazio: 'Selecione a cor' });
  const tinta = selecao([], { vazio: '(opcional) definir depois' });
  const avisoTinta = el('p', { class: 'dica texto-alerta', hidden: true });

  // ---------------------------------------------------------------- rota de etapas (só na inclusão)
  const rota = []; // códigos das etapas, na ordem
  const caixaRota = el('div', { class: 'rota', 'aria-live': 'polite' });
  const nomeEtapa = (codigo) => etapas.find((e) => e.codigo === codigo)?.nome ?? `Etapa ${codigo}`;
  let temPintura = novo ? false : (v.etapas || []).some((e) => ehPintura(e.rotaNome));

  const desenharRota = () => {
    trocar(caixaRota);
    if (!rota.length) {
      caixaRota.append(el('span', { class: 'rota-vazia', text: 'Nenhuma etapa: clique nas etapas abaixo, na ordem do processo.' }));
    }
    rota.forEach((codigo, i) => {
      if (i > 0) caixaRota.append(el('span', { class: 'seta-rota' }, icone('arrow-right', 'icone-3')));
      caixaRota.append(el('span', { class: 'ficha' }, el('b', { text: String(i + 1) }), el('span', { text: nomeEtapa(codigo) }),
        el('button', { type: 'button', 'aria-label': `Tirar ${nomeEtapa(codigo)} da rota`, title: 'Tirar da rota', onclick: () => { rota.splice(i, 1); desenharRota(); } }, icone('x', 'icone-3'))));
    });
    temPintura = rota.some((c) => ehPintura(nomeEtapa(c)));
    atualizarCorETinta();
  };

  const botoesEtapas = el('div', { class: 'botoes-etapa' }, etapas.map((e) => botao(e.nome, {
    tamanho: 'sm', icone: 'plus', onclick: () => { rota.push(e.codigo); desenharRota(); },
  })));
  // RN-ROT-01: só sugere as rotas cujas etapas existem no cadastro.
  const sugestoes = ROTAS_PADRAO.map((nomes) => nomes.map((n) => etapas.find((e) => normalizar(e.nome) === normalizar(n))?.codigo))
    .filter((codigos) => codigos.every((c) => c !== undefined));
  const botoesSugestao = sugestoes.length ? el('div', { class: 'botoes-etapa' },
    el('span', { class: 'texto-xs texto-mudo', text: 'Rotas padrão:' }),
    sugestoes.map((codigos) => botao(codigos.map(nomeEtapa).join(' → '), {
      tamanho: 'sm', variante: 'fantasma', icone: 'route', onclick: () => { rota.splice(0, rota.length, ...codigos); desenharRota(); },
    }))) : null;

  // ---------------------------------------------------------------- cor e tinta (RN-ITE-04, RN-ITE-05, RN-ITE-12)
  function atualizarCorETinta() {
    cor.disabled = !temPintura;
    tinta.disabled = !temPintura || !cor.value;
    if (!temPintura) {
      cor.value = '';
    }
    const corEscolhida = Number(cor.value) || null;
    const daCor = tintas.filter((t) => t.corCodigo === corEscolhida);
    const anterior = tinta.value;
    preencherSelecao(tinta, daCor.map((t) => ({
      valor: t.codigo,
      rotulo: `${t.descricao} · ${t.fornecedorNome}${Number(t.estoqueAtual) > 0 ? ` · ${numero(t.estoqueAtual)} ${t.unidadeEstoque}` : ' · sem estoque'}`,
    })), { vazio: corEscolhida ? (daCor.length ? '(opcional) definir depois' : 'Nenhuma tinta cadastrada nesta cor') : 'Escolha a cor antes' });
    if (daCor.some((t) => String(t.codigo) === anterior)) tinta.value = anterior;
    mostrarAvisoTinta();
  }
  function mostrarAvisoTinta() {
    const t = tintas.find((x) => String(x.codigo) === tinta.value);
    const semEstoque = t && !(Number(t.estoqueAtual) > 0);
    avisoTinta.hidden = !semEstoque;
    avisoTinta.textContent = semEstoque ? 'Atenção: tinta sem estoque.' : '';
  }
  cor.addEventListener('change', atualizarCorETinta);
  tinta.addEventListener('change', mostrarAvisoTinta);

  // ---------------------------------------------------------------- montagem
  const campos = [
    campo('Descrição do item', descricao, { largura: 'c4', obrigatorio: true }),
    campo('Quantidade', qtd, { largura: 'c1', obrigatorio: true }),
    campo('Unidade', unidade, { largura: 'c1', obrigatorio: true }),
  ];
  if (novo) {
    campos.push(el('div', { class: 'campo c6' },
      el('span', { class: 'rotulo' }, 'Etapas da rota', el('span', { class: 'obrigatorio', text: '*' }), ' ', el('span', { class: 'nota', text: '(na ordem em que serão feitas)' })),
      caixaRota, botoesEtapas, botoesSugestao));
  }
  campos.push(campo('Cor pedida', cor, { largura: 'c3', dica: 'Obrigatória quando a rota tem Pintura.' }));
  if (novo) campos.push(el('div', { class: 'c3' }, campo('Tinta', tinta, { dica: 'Só tintas da cor pedida.' }), avisoTinta));
  campos.push(
    campo('Prazo de entrega', prazo, { largura: 'c3' }),
    campo('Valor total do item (R$)', valorItem, { largura: 'c3', dica: 'Em branco: a OS fica com "valor incompleto".' }),
  );
  if (!novo) campos.push(el('p', { class: 'dica c6', text: 'A rota de etapas e a tinta são alteradas na tela da peça (botões da rota e "Definir tinta").' }));

  const cab = titulo || aoRemover ? el('div', { class: 'item-os-cab' },
    el('h4', { class: 'seminegrito texto-sm', text: titulo || '' }),
    aoRemover ? botao('Remover', { variante: 'fantasma', tamanho: 'sm', icone: 'trash-2', onclick: aoRemover }) : null) : null;
  const elemento = el('div', { class: titulo ? 'item-os' : '' }, cab, el('div', { class: 'form-grade' }, campos));

  if (novo) {
    desenharRota();
  } else {
    atualizarCorETinta();
  }

  return {
    elemento,
    focar: () => descricao.focus(),
    /** Erros de digitação que impedem montar o JSON (o resto é conferido pelo back-end). */
    erros() {
      const lista = [];
      if (Number.isNaN(lerDecimal(qtd.value))) lista.push('Quantidade inválida: use números, com vírgula para decimais.');
      if (Number.isNaN(lerDecimal(valorItem.value))) lista.push('Valor do item inválido: use números, com vírgula para os centavos.');
      return lista;
    },
    valor() {
      const base = {
        descricao: textoOuNulo(descricao.value),
        qtdTotal: lerDecimal(qtd.value),
        unidade: unidade.value || null,
        corCodigo: Number(cor.value) || null,
        prazoEntrega: prazo.value || null,
        valorTotalItem: lerDecimal(valorItem.value),
      };
      return novo ? { ...base, tintaCodigo: Number(tinta.value) || null, etapas: [...rota] } : base;
    },
    /** Soma para o rodapé da Nova OS. */
    valorNumerico: () => lerDecimal(valorItem.value),
    rotuloValor: () => moeda(lerDecimal(valorItem.value)),
  };
}
