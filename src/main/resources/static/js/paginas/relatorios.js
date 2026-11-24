// Relatórios (INCLUÍDO em 21/10/2026), no layout do relatorios.tsx do protótipo.
// Cada relatório monta a prévia na tela e exporta CSV (separador ";" e UTF-8 com BOM, que o Excel em português abre
// direto). Os relatórios marcados "Em desenvolvimento" dependem das consultas de produção da Etapa 3 (as mesmas dos
// gráficos do dashboard).
// ALTERADO (18/11/2026) [E3-36]: com a Etapa 3 integrada, nenhum relatório fica mais "Em desenvolvimento":
//  - Ordens de Serviço: view vw_painel_ordens_servico (GET /api/ordens/painel), com tipo/documento do cliente,
//    status atual e as contagens de itens (executados, cancelados, em aberto, atrasados);
//  - Itens atrasados: função fn_itens_atrasados, agora com cliente, dias de atraso, próxima etapa e operador;
//  - Clientes: total por cliente somado sobre o mesmo painel;
//  - Produção diária e Produtividade: view vw_produtividade_funcionario (GET /api/indicadores/produtividade);
//  - Retrabalho mensal (novo): view vw_retrabalho_mensal (GET /api/indicadores/retrabalho/mensal).

import { get } from '../api.js';
// ALTERADO (18/11/2026) [E3-36]: + MESES (nome do mês no relatório de retrabalho mensal).
import { MESES, data, dataHora, documento, formatarCnpj, hojeISO, nItem, nOs, numero, percentualRetrabalho } from '../formato.js';
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
      titulo: 'Ordens de Serviço', desc: 'OS abertas no período (e cliente, se escolhido), com a situação das peças', icone: 'file-text',
      async gerar() {
        // ALTERADO (18/11/2026) [E3-36]: era get('/ordens') com a coluna "Itens" contada em JS; agora é o painel da view,
        // com as contagens feitas no banco e as colunas novas de tipo/documento do cliente e de situação das peças.
        const ordens = (await get('/ordens/painel')).filter((o) => dentroDoPeriodo(o.dataEntrada) && (!cliente.value || String(o.clienteCodigo) === cliente.value));
        return {
          colunas: ['Nº OS', 'Cliente', 'Tipo', 'CPF/CNPJ', 'Status', 'Status desde', 'Prioridade', 'Abertura', 'Registrada por', 'Itens',
            'Executados', 'Cancelados', 'Em aberto', 'Atrasados', 'Valor (R$)', 'Valor incompleto'],
          linhas: ordens.sort((a, b) => a.numero - b.numero).map((o) => [nOs(o.numero), o.clienteNome, o.tipoCliente,
            documento(o.tipoCliente === 'PF' ? o.documentoCliente : null, o.tipoCliente === 'PJ' ? o.documentoCliente : null), o.status,
            dataHora(o.statusDesde), o.prioridade ?? '', dataHora(o.dataEntrada), o.registradaPor || '', String(o.qtdItens),
            String(o.qtdItensExecutados), String(o.qtdItensCancelados), String(o.qtdItensEmAberto), String(o.qtdItensAtrasados),
            numero(o.valorOs), o.valorIncompleto ? 'Sim' : 'Não']),
        };
      },
    },
    {
      titulo: 'Itens atrasados', desc: 'Peças com prazo vencido e não executadas, com a próxima etapa', icone: 'triangle-alert',
      async gerar() {
        // ALTERADO (18/11/2026) [E3-36]: a função fn_itens_atrasados não devolve o valor do item (a coluna saiu) e passou a
        // trazer cliente, dias de atraso, próxima etapa pendente e operador; a lista já vem do mais atrasado ao menos.
        const itens = await get('/itens/atrasados');
        return {
          colunas: ['Item', 'OS', 'Cliente', 'Descrição', 'Quantidade', 'Prazo', 'Dias de atraso', 'Status', 'Próxima etapa', 'Operador'],
          linhas: itens.map((i) => [nItem(i.codigo), nOs(i.osNumero), i.clienteNome, i.descricao, `${numero(i.qtdTotal)} ${i.unidade}`,
            data(i.prazoEntrega), String(i.diasAtraso), i.status, i.etapaPendente || '—', i.operadorEtapa || '—']),
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
        // ALTERADO (18/11/2026) [E3-36]: era get('/ordens'); o painel tem clienteCodigo, dataEntrada, status e valorOs.
        const [clientes, ordens] = await Promise.all([get('/clientes'), get('/ordens/painel')]);
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
    // ALTERADO (18/11/2026) [E3-36]: "Produção diária" e "Produtividade" eram { emDesenvolvimento: true } (sem gerar()).
    // Agora leem a view vw_produtividade_funcionario no período escolhido no topo da tela.
    {
      titulo: 'Produção diária', desc: 'Etapas concluídas por dia no período (com retrabalho e minutos)', icone: 'factory',
      async gerar() {
        const linhas = await get(`/indicadores/produtividade?inicio=${de.value}&fim=${ate.value}`);
        const porDia = new Map();
        linhas.forEach((p) => {
          const r = porDia.get(p.data) || { etapas: 0, retrabalho: 0, minutos: 0, funcionarios: new Set() };
          r.etapas += p.etapasConcluidas;
          r.retrabalho += p.etapasRetrabalho;
          r.minutos += p.minutosTrabalhados;
          r.funcionarios.add(p.funcionarioCodigo);
          porDia.set(p.data, r);
        });
        const dias = [...porDia.entries()].sort((a, b) => a[0].localeCompare(b[0]));
        const total = dias.reduce((t, [, r]) => t + r.etapas, 0);
        return {
          resumo: `${total} etapa(s) concluída(s) de ${data(de.value)} a ${data(ate.value)}.`,
          colunas: ['Data', 'Etapas concluídas', 'Etapas de retrabalho', 'Funcionários', 'Minutos trabalhados'],
          linhas: dias.map(([dia, r]) => [data(dia), String(r.etapas), String(r.retrabalho), String(r.funcionarios.size), String(r.minutos)]),
        };
      },
    },
    {
      titulo: 'Produtividade', desc: 'Etapas concluídas por funcionário e processo no período', icone: 'hard-hat',
      async gerar() {
        const linhas = await get(`/indicadores/produtividade?inicio=${de.value}&fim=${ate.value}`);
        const porChave = new Map();
        linhas.forEach((p) => {
          const chave = `${p.funcionarioCodigo}|${p.processo}`;
          const r = porChave.get(chave) || { nome: p.funcionarioNome, cargo: p.cargo, processo: p.processo, etapas: 0, retrabalho: 0, minutos: 0, dias: new Set() };
          r.etapas += p.etapasConcluidas;
          r.retrabalho += p.etapasRetrabalho;
          r.minutos += p.minutosTrabalhados;
          r.dias.add(p.data);
          porChave.set(chave, r);
        });
        const lista = [...porChave.values()].sort((a, b) => b.etapas - a.etapas || a.nome.localeCompare(b.nome));
        return {
          resumo: 'Minutos médios = minutos trabalhados ÷ etapas concluídas (diferença entre início e conclusão de cada etapa).',
          colunas: ['Funcionário', 'Cargo', 'Processo', 'Dias com produção', 'Etapas concluídas', 'Etapas de retrabalho', 'Minutos trabalhados', 'Minutos médios por etapa'],
          linhas: lista.map((r) => [r.nome, r.cargo, r.processo, String(r.dias.size), String(r.etapas), String(r.retrabalho), String(r.minutos),
            numero(r.etapas ? r.minutos / r.etapas : 0)]),
        };
      },
    },
    // INCLUÍDO (18/11/2026) [E3-36]: retrabalho mês a mês, da view vw_retrabalho_mensal (indicador do PDCA).
    {
      titulo: 'Retrabalho mensal', desc: '% de retrabalho de cada mês (últimos 12 meses, PDCA)', icone: 'rotate-ccw',
      async gerar() {
        const meses = await get('/indicadores/retrabalho/mensal?meses=12');
        return {
          resumo: 'Só aparecem os meses com algum item executado (sem item executado não há base para o %).',
          colunas: ['Mês', 'Itens executados', 'Itens com retrabalho', '% de retrabalho', 'Valor executado (R$)'],
          linhas: meses.map((m) => [`${MESES[m.mes - 1]}/${m.ano}`, String(m.itensExecutados), String(m.itensComRetrabalho),
            `${numero(m.percentual)}%`, numero(m.valorExecutado)]),
        };
      },
    },
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
      // OBSERVAÇÃO (18/11/2026) [E3-36]: desde a integração da Etapa 3 nenhum relatório usa emDesenvolvimento; o tratamento
      // continua aqui para um relatório futuro que dependa de consulta ainda não feita.
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
