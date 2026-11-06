// Relatórios (INCLUÍDO em 21/10/2026), no layout do relatorios.tsx do protótipo.
// Cada relatório monta a prévia na tela e exporta CSV (separador ";" e UTF-8 com BOM, que o Excel em português abre
// direto). Os relatórios marcados "Em desenvolvimento" dependem das consultas de produção da Etapa 3 (as mesmas dos
// gráficos do dashboard).

import { get } from '../api.js';
import { data, dataHora, documento, formatarCnpj, hojeISO, nItem, nOs, numero, percentualRetrabalho } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  baixarArquivo, botao, cabecalho, campo, dialogo, el, entrada, icone, mostrarErro, ocupado, selecao, tabela,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'relatorios', titulo: 'Relatórios' });

if (pagina) {
  const { conteudo } = pagina;
  const de = entrada({ type: 'date', value: hojeISO().slice(0, 8) + '01' });
  const ate = entrada({ type: 'date', value: hojeISO() });
  const cliente = selecao([], { vazio: 'Todos' });
  ref.clientes().then((lista) => {
    [...lista].sort((a, b) => a.nomeExibicao.localeCompare(b.nomeExibicao))
      .forEach((c) => cliente.append(el('option', { value: String(c.codigo), text: c.nomeExibicao })));
  }).catch(() => {});

  const dentroDoPeriodo = (iso) => iso && iso.slice(0, 10) >= de.value && iso.slice(0, 10) <= ate.value;

  /** Cada relatório devolve { colunas: [títulos], linhas: [[valores de texto]] }. */
  const RELATORIOS = [
    {
      titulo: 'Ordens de Serviço', desc: 'OS abertas no período (e cliente, se escolhido)', icone: 'file-text',
      async gerar() {
        const ordens = (await get('/ordens')).filter((o) => dentroDoPeriodo(o.dataEntrada) && (!cliente.value || String(o.clienteCodigo) === cliente.value));
        return {
          colunas: ['Nº OS', 'Cliente', 'Status', 'Prioridade', 'Abertura', 'Registrada por', 'Itens', 'Valor (R$)', 'Valor incompleto'],
          linhas: ordens.sort((a, b) => a.numero - b.numero).map((o) => [nOs(o.numero), o.clienteNome, o.status, o.prioridade ?? '', dataHora(o.dataEntrada),
            o.registradaPor || '', String(o.itens.length), numero(o.valorOs), o.valorIncompleto ? 'Sim' : 'Não']),
        };
      },
    },
    {
      titulo: 'Itens atrasados', desc: 'Peças com prazo vencido e não executadas (RN-ITE-22)', icone: 'triangle-alert',
      async gerar() {
        const itens = await get('/itens/atrasados');
        return {
          colunas: ['Item', 'OS', 'Descrição', 'Quantidade', 'Prazo', 'Status', 'Valor (R$)'],
          linhas: itens.map((i) => [nItem(i.codigo), nOs(i.osNumero), i.descricao, `${numero(i.qtdTotal)} ${i.unidade}`, data(i.prazoEntrega), i.status, numero(i.valorTotalItem)]),
        };
      },
    },
    {
      titulo: 'Retrabalho', desc: '% de retrabalho no período e itens com etapa repetida (PDCA)', icone: 'rotate-ccw',
      async gerar() {
        const q = `?inicio=${de.value}&fim=${ate.value}`;
        const [ind, itens] = await Promise.all([get('/indicadores/retrabalho' + q), get('/indicadores/retrabalho/itens' + q)]);
        return {
          resumo: ind.itensExecutados > 0
            ? `% de retrabalho de ${data(ind.inicio)} a ${data(ind.fim)}: ${percentualRetrabalho(ind, 2)} (${ind.itensComRetrabalho} de ${ind.itensExecutados} itens executados).`
            : `De ${data(ind.inicio)} a ${data(ind.fim)} nenhum item foi executado: sem base para o % de retrabalho.`,
          colunas: ['Item', 'OS', 'Cliente', 'Descrição', 'Finalização', 'Etapas repetidas'],
          linhas: itens.map((i) => [nItem(i.itemCodigo), nOs(i.osNumero), i.clienteNome, i.descricao, dataHora(i.dataFinalizacao), i.etapasRepetidas.join(', ')]),
        };
      },
    },
    {
      titulo: 'Clientes', desc: 'Cadastro com quantidade de OS e total no período', icone: 'users',
      async gerar() {
        const [clientes, ordens] = await Promise.all([get('/clientes'), get('/ordens')]);
        const porCliente = new Map();
        ordens.filter((o) => dentroDoPeriodo(o.dataEntrada)).forEach((o) => {
          const r = porCliente.get(o.clienteCodigo) || { qtd: 0, total: 0 };
          r.qtd++;
          if (o.status !== 'Cancelada') r.total += Number(o.valorOs || 0);
          porCliente.set(o.clienteCodigo, r);
        });
        return {
          colunas: ['Cliente', 'Tipo', 'CPF/CNPJ', 'E-mail', 'Telefone', 'Cidade/UF', 'Fonte', 'Prioridade', 'OS no período', 'Total no período (R$)'],
          linhas: clientes.sort((a, b) => a.nomeExibicao.localeCompare(b.nomeExibicao)).map((c) => {
            const r = porCliente.get(c.codigo) || { qtd: 0, total: 0 };
            return [c.nomeExibicao, c.tipo, documento(c.cpf, c.cnpj), c.email || '', c.telefone || '', c.endereco ? `${c.endereco.cidade}/${c.endereco.uf}` : '',
              c.fonte || '', c.prioridade ?? '', String(r.qtd), numero(r.total)];
          }),
        };
      },
    },
    {
      titulo: 'Fornecedores', desc: 'Prazos de pagamento, situação e tintas por fornecedor', icone: 'truck',
      async gerar() {
        const [fornecedores, tintas] = await Promise.all([get('/fornecedores?somenteAtivos=false'), get('/tintas')]);
        return {
          colunas: ['Fornecedor', 'CNPJ', 'Prazo de pagamento', 'Situação', 'Tintas', 'E-mail', 'Telefone'],
          linhas: fornecedores.sort((a, b) => a.nome.localeCompare(b.nome)).map((f) => [f.nome, formatarCnpj(f.cnpj), f.prazoPagamento || '', f.ativo ? 'Ativo' : 'Inativo',
            String(tintas.filter((t) => t.fornecedorCodigo === f.codigo).length), f.email || '', f.telefone || '']),
        };
      },
    },
    {
      titulo: 'Estoque de tintas', desc: 'Saldo em kg, valor e tintas sem estoque', icone: 'palette',
      async gerar() {
        const tintas = await get('/tintas');
        return {
          colunas: ['Tinta', 'Cor', 'Fornecedor', 'Tipo', 'Acabamento', 'Estoque (kg)', 'Valor por kg (R$)', 'Observação'],
          linhas: tintas.sort((a, b) => a.descricao.localeCompare(b.descricao)).map((t) => [t.descricao, t.corNome, t.fornecedorNome, t.tipo || '', t.acabamento || '',
            numero(t.estoqueAtual), numero(t.valorUnitario), [Number(t.estoqueAtual) > 0 ? '' : 'sem estoque', t.precoAConfirmar ? 'preço a confirmar' : ''].filter(Boolean).join('; ')]),
        };
      },
    },
    { titulo: 'Produção diária', desc: 'Etapas concluídas por dia', icone: 'factory', emDesenvolvimento: true },
    { titulo: 'Produtividade', desc: 'Etapas concluídas por funcionário', icone: 'hard-hat', emDesenvolvimento: true },
  ];

  function csv(rel) {
    const escapar = (v) => {
      const t = String(v ?? '');
      return /[";\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
    };
    const linhas = [rel.colunas, ...rel.linhas].map((l) => l.map(escapar).join(';'));
    return '﻿' + linhas.join('\r\n');
  }

  async function gerar(def, botaoEl) {
    await ocupado(botaoEl, async () => {
      try {
        const rel = await def.gerar();
        const nomeArquivo = `fenix-${def.titulo.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, '-')}-${hojeISO()}.csv`;
        const baixar = botao('Baixar CSV', { variante: 'primario', icone: 'download', onclick: () => baixarArquivo(nomeArquivo, csv(rel)) });
        const dlg = dialogo({
          titulo: def.titulo, largura: '4xl',
          descricao: `${rel.linhas.length} linha(s). ${rel.resumo || ''} A prévia mostra até 50 linhas; o CSV leva todas.`,
          conteudo: el('div', { class: 'card card-tabela' }, tabela({
            colunas: rel.colunas.map((c) => ({ titulo: c })),
            linhas: rel.linhas.slice(0, 50),
            textoVazio: 'Nenhum registro para os filtros escolhidos.',
            montar: (l) => l.map((v) => String(v ?? '')),
          })),
          botoes: [botao('Fechar', { onclick: () => dlg.fechar() }), baixar],
        });
      } catch (e) {
        mostrarErro(e);
      }
    });
  }

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Relatórios', 'Gere e exporte relatórios analíticos (CSV para Excel).'),
    el('div', { class: 'card card-p4' }, el('div', { class: 'grade sm-3' },
      campo('Período de', de), campo('Até', ate), campo('Cliente (relatório de OS)', cliente))),
    el('div', { class: 'grade sm-2 lg-3 xl-4' }, RELATORIOS.map((r) => {
      const gerarBtn = botao(r.emDesenvolvimento ? 'Em desenvolvimento' : 'Gerar relatório', { tamanho: 'sm', classe: 'btn-bloco', disabled: r.emDesenvolvimento });
      if (!r.emDesenvolvimento) gerarBtn.addEventListener('click', () => gerar(r, gerarBtn));
      const baixarBtn = r.emDesenvolvimento ? null : botao('', { variante: 'fantasma', tamanho: 'icone-sm', icone: 'download', titulo: `Gerar ${r.titulo}`, onclick: (ev) => gerar(r, ev.currentTarget) });
      return el('div', { class: 'card realce' },
        el('div', { class: 'card-cab' },
          el('div', { class: 'linha-entre' }, el('div', { class: 'indicador-icone destaque-primary' }, icone(r.icone)), baixarBtn),
          el('h3', { class: 'card-titulo mt-3', text: r.titulo }),
          el('p', { class: 'card-desc texto-xs', text: r.emDesenvolvimento ? `${r.desc} — depende das consultas da Etapa 3.` : r.desc })),
        el('div', { class: 'card-corpo' }, gerarBtn));
    }))));
}
