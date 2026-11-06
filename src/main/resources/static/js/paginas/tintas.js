// Tintas (INCLUÍDO em 21/10/2026), no layout do tintas.tsx do protótipo (cards com a faixa da cor).
// Regras: só fornecedor ativo recebe tinta nova (RN-FOR-03); o estoque é em kg e muda só por ajuste — entrada de
// compra ou baixa — sem ficar negativo (RN-TIN-14 e RN-TIN-15); valor 0 aparece como "preço a confirmar" (RN-TIN-18);
// tipo e acabamento vêm de listas padronizadas (RN-TIN-08 e RN-TIN-10, definidas em 22/10 a partir do estoque, em config.js).
// Permissões: ADMIN e OPERADOR cadastram, alteram e ajustam o estoque; excluir é só do ADMIN.

import { del, get, post, put } from '../api.js';
import { ACABAMENTOS_TINTA, TIPOS_TINTA } from '../config.js';
import { decimalParaCampo, lerDecimal, moeda, normalizar, numero, textoOuNulo } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  avisoAcesso, badge, botao, cabecalho, caixaInfo, campo, campoBusca, confirmar, dialogo, el, entrada, esqueleto,
  interruptor, mostrarErro, ocupado, parametroUrl, preencherSelecao, selecao, sucesso, trocar, vazio,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'tintas', titulo: 'Tintas' });

if (pagina) {
  const { conteudo, admin } = pagina;
  const filtros = { busca: '', cor: '', fornecedor: parametroUrl('fornecedor') || '', semEstoque: parametroUrl('semEstoque') === '1' };
  let tintas = [];
  let cores = [];
  let fornecedores = [];

  const filtroCor = selecao([], { vazio: 'Todas as cores', 'aria-label': 'Cor' });
  filtroCor.addEventListener('change', () => { filtros.cor = filtroCor.value; desenhar(); });
  const filtroFornecedor = selecao([], { vazio: 'Todos os fornecedores', 'aria-label': 'Fornecedor' });
  filtroFornecedor.addEventListener('change', () => { filtros.fornecedor = filtroFornecedor.value; desenhar(); });
  [filtroCor, filtroFornecedor].forEach((s) => s.classList.add('largura-filtro'));
  const grade = el('div', { class: 'grade sm-2 lg-3 xl-4' }, esqueleto(1, 'esqueleto-bloco'));
  const resumo = el('p', { class: 'texto-xs texto-mudo' });

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Tintas', 'Catálogo e estoque de tintas (em kg). Clique em uma tinta para visualizar, editar ou ajustar o estoque.', [
      botao('Nova tinta', { variante: 'primario', icone: 'plus', onclick: () => dialogoTinta().catch(mostrarErro) }),
    ]),
    avisoAcesso(admin, 'Perfil Operador — você cadastra, altera e ajusta o estoque; excluir tinta é com o Administrador.'),
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' },
      campoBusca('Buscar tinta (descrição, cor, fornecedor ou referência)...', (v) => { filtros.busca = v; desenhar(); }, { classe: 'flex-1' }),
      filtroCor, filtroFornecedor,
      interruptor('Sem estoque', { marcado: filtros.semEstoque, aoMudar: (v) => { filtros.semEstoque = v; desenhar(); } }))),
    grade, resumo));

  const semEstoque = (t) => !(Number(t.estoqueAtual) > 0);

  function desenhar() {
    const termo = normalizar(filtros.busca);
    const lista = tintas.filter((t) => {
      if (filtros.cor && String(t.corCodigo) !== filtros.cor) return false;
      if (filtros.fornecedor && String(t.fornecedorCodigo) !== filtros.fornecedor) return false;
      if (filtros.semEstoque && !semEstoque(t)) return false;
      return !termo || normalizar(`${t.descricao} ${t.corNome} ${t.fornecedorNome} ${t.referenciaFornecedor || ''} ${t.tipo || ''}`).includes(termo);
    });
    resumo.textContent = `${lista.length} de ${tintas.length} tintas`;
    trocar(grade, lista.length ? lista.map((t) => el('div', { class: 'card clicavel', role: 'button', tabindex: 0, onclick: () => dialogoTinta(t).catch(mostrarErro), onkeydown: (ev) => { if (ev.key === 'Enter') dialogoTinta(t).catch(mostrarErro); } },
      el('div', { class: 'tinta-faixa' }, el('span', { class: 'truncar', text: t.corNome })),
      el('div', { class: 'card-p4 pilha-2' },
        el('div', { class: 'linha-entre' }, el('h3', { class: 'seminegrito texto-sm', text: t.descricao }), t.tipo ? el('span', { class: 'badge badge-contorno badge-10', text: t.tipo }) : null),
        el('div', { class: 'grade sm-2 grade-3 texto-xs' },
          el('div', {}, el('span', { class: 'texto-mudo', text: 'Acabamento: ' }), el('span', { class: 'medio', text: t.acabamento || '—' })),
          el('div', {}, el('span', { class: 'texto-mudo', text: 'Estoque: ' }), semEstoque(t)
            ? el('span', { class: 'texto-alerta negrito', text: 'sem estoque' })
            : el('span', { class: 'negrito', text: `${numero(t.estoqueAtual)} ${t.unidadeEstoque}` }))),
        el('div', { class: 'divisor-topo linha-entre' },
          el('span', { class: 'texto-xs texto-mudo truncar', text: `${t.fornecedorNome}${t.fornecedorAtivo ? '' : ' (inativo)'}` }),
          t.precoAConfirmar ? badge('preço a confirmar', 'alerta') : el('span', { class: 'negrito texto-primario texto-sm sem-quebra', text: `${moeda(t.valorUnitario)}/${t.unidadeEstoque}` })))))
      : el('div', { class: 'card sm-span-6' }, vazio(tintas.length ? 'Nenhuma tinta com esses filtros.' : 'Nenhuma tinta cadastrada.', 'palette')));
  }

  /** Lista padronizada + valores já gravados (para não perder o que veio da carga inicial). */
  function opcoesLista(base, campoTinta) {
    const existentes = tintas.map((t) => t[campoTinta]).filter(Boolean);
    const todas = [...base];
    existentes.forEach((v) => { if (!todas.some((x) => normalizar(x) === normalizar(v))) todas.push(v); });
    return todas.sort((a, b) => a.localeCompare(b));
  }

  async function dialogoTinta(t = null) {
    // o botão pode ser clicado antes de as listas carregarem
    if (!cores.length) cores = await ref.cores();
    if (!fornecedores.length) fornecedores = (await get('/fornecedores?somenteAtivos=false')).sort((a, b) => a.nome.localeCompare(b.nome));
    const novo = !t;
    const listaFornecedores = fornecedores.filter((f) => f.ativo || f.codigo === t?.fornecedorCodigo);
    const fornecedor = selecao(listaFornecedores.map((f) => ({ valor: f.codigo, rotulo: f.ativo ? f.nome : `${f.nome} (inativo)` })), { vazio: 'Selecione um fornecedor ativo', valor: t?.fornecedorCodigo ?? '' });
    const cor = selecao(cores.map((c) => ({ valor: c.codigo, rotulo: c.nome })), { vazio: 'Selecione a cor', valor: t?.corCodigo ?? '' });
    const descricao = entrada({ maxlength: 100, value: t?.descricao ?? '', placeholder: 'Ex.: Poliéster vermelho RAL 3020' });
    const tipo = selecao(opcoesLista(TIPOS_TINTA, 'tipo'), { vazio: '(sem tipo)', valor: t?.tipo ?? '' });
    const acabamento = selecao(opcoesLista(ACABAMENTOS_TINTA, 'acabamento'), { vazio: '(sem acabamento)', valor: t?.acabamento ?? '' });
    const referencia = entrada({ maxlength: 100, value: t?.referenciaFornecedor ?? '', placeholder: 'Código usado pelo fornecedor' });
    const valor = entrada({ inputmode: 'decimal', value: t ? decimalParaCampo(t.valorUnitario) : '', placeholder: '0,00' });

    const partes = [el('div', { class: 'form-grade' },
      campo('Fornecedor', fornecedor, { largura: 'c6', obrigatorio: true, dica: 'Só fornecedores ativos recebem tinta nova (RN-FOR-03).' }),
      campo('Descrição da tinta', descricao, { largura: 'c6', obrigatorio: true }),
      campo('Cor', cor, { largura: 'c3', obrigatorio: true, dica: novo ? undefined : 'Tinta já usada em itens não muda de cor.' }),
      campo('Referência do fornecedor', referencia, { largura: 'c3' }),
      campo('Tipo', tipo, { largura: 'c2', dica: 'Lista do estoque da Fênix (RN-TIN-08).' }),
      campo('Acabamento', acabamento, { largura: 'c2', dica: 'Lista do estoque da Fênix (RN-TIN-10).' }),
      campo('Valor por kg (R$)', valor, { largura: 'c2', obrigatorio: true, dica: '0 = preço a confirmar.' }))];
    if (novo) partes.push(caixaInfo('O estoque começa em 0 kg e muda só por ajuste (entrada de compra ou baixa), na tela da tinta (RN-TIN-14).'));

    // Ajuste de estoque (só na tinta já cadastrada)
    if (!novo) {
      const tipoAjuste = selecao([{ valor: 'entrada', rotulo: 'Entrada de compra (+)' }, { valor: 'baixa', rotulo: 'Baixa / consumo (−)' }], { valor: 'entrada' });
      const qtd = entrada({ inputmode: 'decimal', placeholder: '0,00' });
      const ajustar = botao('Registrar ajuste', { icone: 'check' });
      ajustar.addEventListener('click', () => ocupado(ajustar, async () => {
        const q = lerDecimal(qtd.value);
        if (!q || Number.isNaN(q) || q <= 0) { dlg.mostrarErro({ mensagens: ['Informe a quantidade do ajuste em kg (maior que zero), com até 2 casas.'] }); return; }
        try {
          const r = await post(`/tintas/${t.codigo}/estoque`, { quantidade: tipoAjuste.value === 'baixa' ? -q : q });
          dlg.fechar();
          sucesso(`Estoque de ${r.descricao}: ${numero(r.estoqueAtual)} ${r.unidadeEstoque}.`);
          ref.esquecer('tintas');
          await carregar();
        } catch (e) {
          dlg.mostrarErro(e); // ex.: "Estoque insuficiente: saldo atual ..."
        }
      }));
      partes.push(el('section', { class: 'secao-form card card-p4' },
        el('div', { class: 'linha-entre' }, el('h3', { class: 'secao-titulo', text: 'Estoque' }),
          semEstoque(t) ? badge('sem estoque', 'alerta') : el('span', { class: 'texto-lg negrito texto-primario', text: `${numero(t.estoqueAtual)} ${t.unidadeEstoque}` })),
        el('div', { class: 'form-grade' }, campo('Movimento', tipoAjuste, { largura: 'c3' }), campo('Quantidade (kg)', qtd, { largura: 'c2' }),
          el('div', { class: 'campo c1' }, el('span', { class: 'rotulo', text: ' ' }), ajustar)),
        el('p', { class: 'dica', text: 'O saldo nunca fica negativo; a baixa maior que o saldo é recusada.' })));
    }

    const salvar = botao(novo ? 'Cadastrar' : 'Salvar alterações', { variante: 'primario', icone: 'save' });
    const esquerda = el('div', { class: 'esquerda' });
    if (!novo && admin) {
      const excluir = botao('Excluir', { variante: 'perigo-contorno', icone: 'trash-2' });
      excluir.addEventListener('click', async () => {
        if (!(await confirmar(`Excluir a tinta ${t.descricao}? Tinta usada em algum item não é excluída.`, { titulo: 'Excluir tinta', textoConfirmar: 'Excluir', perigo: true }))) return;
        await ocupado(excluir, async () => {
          try {
            await del(`/tintas/${t.codigo}`);
            dlg.fechar();
            sucesso('Tinta excluída.');
            ref.esquecer('tintas');
            await carregar();
          } catch (e) {
            dlg.mostrarErro(e);
          }
        });
      });
      esquerda.append(excluir);
    }
    const dlg = dialogo({
      titulo: novo ? 'Cadastrar nova tinta' : 'Editar tinta', largura: '2xl',
      descricao: novo ? undefined : `${t.corNome} · ${t.fornecedorNome}${t.precoAConfirmar ? ' · preço a confirmar' : ''}`,
      conteudo: el('div', { class: 'pilha' }, partes),
      botoes: [esquerda, botao(novo ? 'Cancelar' : 'Fechar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      const v = lerDecimal(valor.value);
      if (Number.isNaN(v)) { dlg.mostrarErro({ mensagens: ['Valor por kg inválido: use números, com vírgula para os centavos.'] }); return; }
      const corpo = {
        fornecedorCodigo: Number(fornecedor.value) || null, corCodigo: Number(cor.value) || null, descricao: textoOuNulo(descricao.value),
        tipo: tipo.value || null, acabamento: acabamento.value || null, referenciaFornecedor: textoOuNulo(referencia.value), valorUnitario: v,
      };
      try {
        const r = novo ? await post('/tintas', corpo) : await put(`/tintas/${t.codigo}`, corpo);
        dlg.fechar();
        sucesso(novo ? `Tinta ${r.descricao} cadastrada.` : 'Tinta atualizada.');
        ref.esquecer('tintas');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function carregar() {
    try {
      const [lista, listaCores, listaFornecedores] = await Promise.all([get('/tintas'), ref.cores(), get('/fornecedores?somenteAtivos=false')]);
      tintas = lista.sort((a, b) => a.descricao.localeCompare(b.descricao));
      cores = listaCores;
      fornecedores = listaFornecedores.sort((a, b) => a.nome.localeCompare(b.nome));
      preencherSelecao(filtroCor, cores.map((c) => ({ valor: c.codigo, rotulo: c.nome })), { vazio: 'Todas as cores', valor: filtros.cor });
      preencherSelecao(filtroFornecedor, fornecedores.map((f) => ({ valor: f.codigo, rotulo: f.nome })), { vazio: 'Todos os fornecedores', valor: filtros.fornecedor });
      desenhar();
    } catch (e) {
      trocar(grade, el('div', { class: 'card sm-span-6' }, vazio('Não foi possível carregar as tintas.', 'circle-alert')));
      mostrarErro(e);
    }
  }

  carregar();
}
