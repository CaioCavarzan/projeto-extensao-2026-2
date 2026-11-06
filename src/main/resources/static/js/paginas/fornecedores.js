// Fornecedores (INCLUÍDO em 21/10/2026), no layout do fornecedores.tsx do protótipo.
// Permissões (RN-SIS-03): ADMIN e OPERADOR cadastram e alteram; inativar, reativar, excluir e corrigir CNPJ é só do
// ADMIN. Fornecedor inativo não recebe tinta nova (RN-FOR-03). Prazo de pagamento da lista padronizada (RN-FOR-09).
// Especialização: o fornecedor pode ser também cliente PJ (mesmo CNPJ); fornecedor nunca é funcionário.

import { del, get, post, put } from '../api.js';
import { camposPessoa, selosPapeis, TEXTO_HERANCA } from '../componentes/pessoa.js';
import { cnpjLimpo, formatarCnpj, normalizar } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  avisoAcesso, badge, botao, cabecalho, caixaInfo, campo, campoBusca, confirmar, dialogo, el, entrada, esqueleto, icone,
  interruptor, mostrarAvisos, mostrarErro, ocupado, pedirTexto, selecao, sucesso, tabela, trocar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'fornecedores', titulo: 'Fornecedores' });

if (pagina) {
  const { conteudo, admin } = pagina;
  const filtros = { busca: '', soAtivos: false };
  let fornecedores = [];
  let tintasPorFornecedor = new Map();
  let parametros = null;
  const areaTabela = el('div', {}, esqueleto(6));

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Fornecedores', 'Clique em um fornecedor para visualizar ou editar. Vinculados ao catálogo de Tintas.', [
      botao('Novo fornecedor', { variante: 'primario', icone: 'plus', onclick: () => dialogoNovo().catch(mostrarErro) }),
    ]),
    avisoAcesso(admin, 'Perfil Operador — você cadastra e altera; inativar, reativar, excluir e corrigir CNPJ é com o Administrador.'),
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' },
      campoBusca('Buscar fornecedor (nome ou CNPJ)...', (v) => { filtros.busca = v; desenhar(); }, { classe: 'flex-1' }),
      interruptor('Só ativos', { aoMudar: (v) => { filtros.soAtivos = v; desenhar(); } }))),
    el('div', { class: 'card card-tabela' }, areaTabela)));

  function desenhar() {
    const termo = normalizar(filtros.busca);
    const lista = fornecedores.filter((f) => (!filtros.soAtivos || f.ativo)
      && (!termo || normalizar(f.nome).includes(termo) || f.cnpj.includes(cnpjLimpo(filtros.busca) || '#')));
    trocar(areaTabela, tabela({
      colunas: [{ titulo: 'Nome' }, { titulo: 'CNPJ' }, { titulo: 'Prazo de pagamento' }, { titulo: 'Tintas vinculadas' }, { titulo: 'Status' }, { titulo: '' }],
      linhas: lista,
      textoVazio: fornecedores.length ? 'Nenhum fornecedor com esses filtros.' : 'Nenhum fornecedor cadastrado.',
      aoClicar: (f) => dialogoVer(f),
      montar: (f) => {
        const qtd = tintasPorFornecedor.get(f.codigo) || 0;
        return [
          el('div', { class: 'coluna' }, el('span', { class: 'medio', text: f.nome }), selosPapeis(f.papeis, ['Fornecedor'])),
          el('span', { class: 'mono texto-xs texto-mudo sem-quebra', text: formatarCnpj(f.cnpj) }),
          badge(f.prazoPagamento || '—', 'info-contorno'),
          qtd ? el('a', { class: 'linha texto-xs medio texto-primario', href: `tintas.html?fornecedor=${f.codigo}` }, icone('palette', 'icone-35'), `${qtd} ${qtd === 1 ? 'tinta' : 'tintas'}`)
            : el('span', { class: 'texto-xs texto-mudo', text: '—' }),
          f.ativo ? badge('Ativo', 'sucesso') : badge('Inativo', 'mudo'),
          { conteudo: el('div', { class: 'acoes' }, botao('', { variante: 'fantasma', tamanho: 'icone-sm', icone: 'eye', titulo: 'Ver fornecedor', onclick: () => dialogoVer(f) })) },
        ];
      },
    }));
  }

  function montarFormulario(f = null) {
    const novo = !f;
    const pessoa = camposPessoa({ valor: f ? { nome: f.nome, email: f.email, telefone: f.telefone, endereco: f.endereco } : {}, rotuloNome: 'Nome / Razão social' });
    pessoa.entradaNome.placeholder = 'Ex.: Tintas Premium Ltda';
    const cnpj = entrada({ placeholder: '00.000.000/0000-00', value: f ? formatarCnpj(f.cnpj) : '', readOnly: !novo, autocomplete: 'off' });
    cnpj.addEventListener('input', () => { cnpj.value = formatarCnpj(cnpj.value); });
    const prazo = selecao(parametros.prazosPagamento, { vazio: 'Selecione', valor: f?.prazoPagamento ?? '' });
    const campoCnpj = campo('CNPJ', cnpj, { largura: 'c2', obrigatorio: novo, dica: novo ? 'Aceita o CNPJ alfanumérico.' : undefined });
    if (!novo && admin) {
      campoCnpj.append(botao('Corrigir CNPJ', { tamanho: 'sm', variante: 'fantasma', icone: 'pencil', onclick: () => corrigirCnpj(f) }));
    }
    const elemento = el('div', { class: 'pilha' },
      el('div', { class: 'form-grade' }, pessoa.nome, campoCnpj, pessoa.email, pessoa.telefone,
        campo('Prazo de pagamento', prazo, { largura: 'c3', obrigatorio: true, dica: 'Lista padronizada (RN-FOR-09).' })),
      el('section', { class: 'secao-form' }, el('h3', { class: 'secao-titulo', text: 'Endereço' }), pessoa.endereco),
      novo ? caixaInfo(TEXTO_HERANCA) : null);
    return {
      elemento,
      valor: () => (novo
        ? { pessoa: pessoa.valor(), cnpj: cnpjLimpo(cnpj.value), prazoPagamento: prazo.value || null }
        : { pessoa: pessoa.valor(), prazoPagamento: prazo.value || null }),
    };
  }

  async function dialogoNovo() {
    parametros = parametros || await ref.parametros(); // o botão pode ser clicado antes de a lista carregar
    const form = montarFormulario();
    const salvar = botao('Cadastrar', { variante: 'primario', icone: 'save' });
    const dlg = dialogo({ titulo: 'Cadastrar novo fornecedor', conteudo: form.elemento, largura: '3xl', botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar] });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      try {
        const f = await post('/fornecedores', form.valor());
        dlg.fechar();
        sucesso(`Fornecedor ${f.nome} cadastrado.`);
        mostrarAvisos(f.avisos);
        ref.esquecer('fornecedores');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  function dialogoVer(f) {
    const form = montarFormulario(f);
    const esquerda = el('div', { class: 'esquerda' });
    if (admin) {
      const ativo = botao(f.ativo ? 'Inativar' : 'Reativar', { variante: f.ativo ? 'perigo-contorno' : 'contorno', icone: f.ativo ? 'ban' : 'refresh-cw' });
      ativo.addEventListener('click', async () => {
        if (f.ativo && !(await confirmar(`Inativar ${f.nome}? Fornecedor inativo não recebe tinta nova; as tintas já cadastradas continuam (RN-FOR-03).`, { titulo: 'Inativar fornecedor', textoConfirmar: 'Inativar', perigo: true }))) return;
        await acao(ativo, () => post(`/fornecedores/${f.codigo}/${f.ativo ? 'inativar' : 'reativar'}`), f.ativo ? 'Fornecedor inativado.' : 'Fornecedor reativado.');
      });
      const excluir = botao('Excluir', { variante: 'perigo-contorno', icone: 'trash-2' });
      excluir.addEventListener('click', async () => {
        if (!(await confirmar(`Excluir ${f.nome}? Fornecedor com tintas não é excluído: inative-o (RN-SIS-12).`, { titulo: 'Excluir fornecedor', textoConfirmar: 'Excluir', perigo: true }))) return;
        await acao(excluir, () => del(`/fornecedores/${f.codigo}`), 'Fornecedor excluído.');
      });
      esquerda.append(ativo, excluir);
    }
    const salvar = botao('Salvar alterações', { variante: 'primario', icone: 'save' });
    const dlg = dialogo({
      titulo: 'Editar fornecedor', largura: '3xl',
      descricao: `${f.ativo ? 'Ativo' : 'Inativo'} · ${tintasPorFornecedor.get(f.codigo) || 0} tinta(s) vinculada(s)${f.papeis?.length > 1 ? ` · papéis: ${f.papeis.join(' · ')}` : ''}.`,
      conteudo: form.elemento,
      botoes: [esquerda, botao('Fechar', { onclick: () => dlg.fechar() }), salvar],
    });
    async function acao(b, chamada, mensagem) {
      await ocupado(b, async () => {
        try {
          await chamada();
          dlg.fechar();
          sucesso(mensagem);
          ref.esquecer('fornecedores');
          await carregar();
        } catch (e) {
          dlg.mostrarErro(e);
        }
      });
    }
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      try {
        const r = await put(`/fornecedores/${f.codigo}`, form.valor());
        dlg.fechar();
        sucesso('Fornecedor atualizado.');
        mostrarAvisos(r.avisos);
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function corrigirCnpj(f) {
    const novo = await pedirTexto({
      titulo: 'Corrigir CNPJ', rotulo: 'Novo CNPJ', valor: formatarCnpj(f.cnpj),
      mascara: (e) => e.addEventListener('input', () => { e.value = formatarCnpj(e.value); }),
      dica: 'Vale para todos os papéis da pessoa que usam o CNPJ (fornecedor e cliente PJ).',
    });
    if (novo === null) return;
    try {
      const r = await put(`/pessoas/${f.codigo}/cnpj`, { documento: cnpjLimpo(novo) });
      sucesso('CNPJ corrigido.');
      mostrarAvisos(r?.mensagens);
      document.querySelectorAll('dialog').forEach((d) => d.close());
      await carregar();
    } catch (e) {
      mostrarErro(e);
    }
  }

  async function carregar() {
    try {
      const [lista, tintas, p] = await Promise.all([get('/fornecedores?somenteAtivos=false'), get('/tintas'), ref.parametros()]);
      fornecedores = lista.sort((a, b) => a.nome.localeCompare(b.nome));
      parametros = p;
      tintasPorFornecedor = new Map();
      tintas.forEach((t) => tintasPorFornecedor.set(t.fornecedorCodigo, (tintasPorFornecedor.get(t.fornecedorCodigo) || 0) + 1));
      desenhar();
    } catch (e) {
      trocar(areaTabela, el('div', { class: 'vazio' }, icone('circle-alert'), el('p', { text: 'Não foi possível carregar os fornecedores.' })));
      mostrarErro(e);
    }
  }

  carregar();
}
