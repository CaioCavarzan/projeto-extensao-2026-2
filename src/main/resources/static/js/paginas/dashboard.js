// Dashboard (tela inicial) — INCLUÍDO em 21/10/2026, no layout do index.tsx do protótipo.
// Indicadores do topo: GET /api/dashboard/resumo (dados reais). Gráficos: GET /api/dashboard/graficos/{nome}
// (programados; os dados serão desenvolvidos depois — ver graficos.js). Alertas e próximas etapas: dados reais.

import { get } from '../api.js';
import { GRAFICOS_PAINEL } from '../config.js';
import { diaMes, data, nItem, nOs, percentualRetrabalho } from '../formato.js';
import { cartaoGrafico } from '../graficos.js';
import { montarPagina } from '../layout.js';
import { cabecalho, cartao, el, esqueleto, icone, indicador, mostrarErro, status, tabela, trocar } from '../ui.js';

const pagina = await montarPagina({ pagina: 'dashboard', titulo: 'Dashboard' });

if (pagina) {
  const { conteudo } = pagina;
  const k = {
    abertas: indicador({ titulo: 'OS em aberto', nomeIcone: 'clipboard-list', destaque: 'info', dica: 'não encerradas' }),
    concluidas: indicador({ titulo: 'Concluídas', nomeIcone: 'circle-check', destaque: 'success', dica: 'OS concluídas' }),
    producao: indicador({ titulo: 'Produção hoje', nomeIcone: 'factory', destaque: 'primary', dica: 'etapas' }),
    retrabalho: indicador({ titulo: 'Retrabalho', nomeIcone: 'rotate-ccw', destaque: 'warning', dica: 'no mês' }),
    atrasos: indicador({ titulo: 'Atrasos', nomeIcone: 'triangle-alert', destaque: 'destructive', dica: 'itens vencidos' }),
    cronograma: indicador({ titulo: 'Cronograma', nomeIcone: 'calendar-clock', destaque: 'primary', dica: 'etapas hoje' }),
  };

  const listaAlertas = el('div', { class: 'pilha-3' }, esqueleto(3, 'esqueleto-bloco'));
  const cardAlertas = cartao({
    titulo: 'Alertas críticos', descricao: 'Requerem atenção imediata', classe: 'brilho', corpo: listaAlertas,
  });
  cardAlertas.querySelector('.card-titulo').prepend(icone('triangle-alert', 'texto-primario'));
  cardAlertas.querySelector('.card-titulo').classList.add('linha');

  const areaFila = el('div', {}, esqueleto(4));
  const cardFila = cartao({
    titulo: 'Próximas etapas', descricao: 'Fila de produção: etapas em execução e prontas para iniciar', classe: 'lg-span-2', corpo: areaFila,
    acoes: el('a', { class: 'btn btn-contorno btn-sm', href: 'producao.html' }, icone('factory'), el('span', { text: 'Abrir produção' })),
  });

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Dashboard Operacional', 'Visão geral da produção, OS e indicadores em tempo real.', [
      el('a', { class: 'btn btn-primario', href: 'ordem-nova.html' }, icone('arrow-up-right'), el('span', { text: 'Nova OS' })),
    ]),
    el('div', { class: 'grade sm-2 lg-3 xl-6' }, Object.values(k)),
    el('div', { class: 'grade lg-2' }, cartaoGrafico(GRAFICOS_PAINEL.producaoDiaria), cartaoGrafico(GRAFICOS_PAINEL.retrabalhoMensal)),
    el('div', { class: 'grade lg-3' }, cartaoGrafico(GRAFICOS_PAINEL.etapasPorFuncionario, { classe: 'lg-span-2' }), cardAlertas),
    el('div', { class: 'grade lg-3' }, cartaoGrafico(GRAFICOS_PAINEL.osPorStatus), cardFila)));

  try {
    const [resumo, atrasados, fila] = await Promise.all([get('/dashboard/resumo'), get('/itens/atrasados'), get('/etapas-item/fila')]);
    const r = resumo.retrabalhoMes || {};
    k.abertas.definir(resumo.osAbertas);
    k.concluidas.definir(resumo.osPorStatus?.['Concluída'] ?? 0);
    k.producao.definir(resumo.etapasConcluidasHoje, `etapas · ${resumo.etapasEmExecucao} em execução`);
    k.retrabalho.definir(percentualRetrabalho(r), r.itensExecutados > 0
      ? `${r.itensComRetrabalho} de ${r.itensExecutados} itens no mês` : 'nenhum item executado no mês');
    k.atrasos.definir(resumo.itensAtrasados);
    k.cronograma.definir(resumo.etapasNoCronogramaHoje, 'etapas hoje');

    // Alertas críticos: itens atrasados (RN-ITE-22), OS pausadas e tintas sem estoque.
    const alertas = atrasados.slice(0, 4).map((i) => el('a', { class: 'item-lista', href: `peca.html?codigo=${i.codigo}` },
      el('div', { class: 'flex-1' },
        el('p', { class: 'texto-sm seminegrito', text: `${nOs(i.osNumero)} · item ${nItem(i.codigo)}` }),
        el('p', { class: 'texto-xs texto-mudo truncar', text: i.descricao }),
        el('p', { class: 'texto-xs mt-1', text: `Prazo ${data(i.prazoEntrega)}` })),
      status('Atrasada')));
    if (atrasados.length > 4) alertas.push(el('a', { class: 'btn btn-fantasma btn-sm', href: 'pecas.html?atrasadas=1', text: `Ver os ${atrasados.length} itens atrasados` }));
    const pausadas = resumo.osPorStatus?.['Pausada'] || 0;
    if (pausadas) {
      alertas.push(el('a', { class: 'item-lista', href: 'ordens.html?status=Pausada' },
        el('div', {}, el('p', { class: 'texto-sm seminegrito', text: `${pausadas} OS pausada(s)` }), el('p', { class: 'texto-xs texto-mudo', text: 'Retome a OS para continuar a produção.' })),
        status('Pausada')));
    }
    if (resumo.tintasSemEstoque) {
      alertas.push(el('a', { class: 'item-lista', href: 'tintas.html?semEstoque=1' },
        el('div', {}, el('p', { class: 'texto-sm seminegrito', text: `${resumo.tintasSemEstoque} tinta(s) sem estoque` }), el('p', { class: 'texto-xs texto-mudo', text: 'Registre a entrada de compra no estoque.' })),
        el('span', { class: 'badge badge-alerta', text: 'Estoque' })));
    }
    trocar(listaAlertas, alertas.length ? alertas : el('div', { class: 'vazio' }, icone('circle-check'), el('p', { text: 'Nenhum alerta crítico no momento.' })));

    // Próximas etapas: em execução primeiro, depois as que podem iniciar (a fila já vem por prazo).
    const proximas = [...fila.filter((e) => e.dataInicio), ...fila.filter((e) => !e.dataInicio && e.podeIniciar)].slice(0, 6);
    trocar(areaFila, tabela({
      colunas: [{ titulo: 'OS' }, { titulo: 'Item' }, { titulo: 'Etapa' }, { titulo: 'Prazo' }, { titulo: 'Situação' }],
      linhas: proximas,
      textoVazio: 'Nenhuma etapa em execução ou pronta para iniciar.',
      classeLinha: (e) => (e.atrasado ? 'linha-atrasada' : ''),
      aoClicar: (e) => { location.href = `peca.html?codigo=${e.itemCodigo}`; },
      montar: (e) => [
        el('span', { class: 'mono seminegrito texto-primario', text: nOs(e.osNumero) }),
        el('span', { class: 'truncar', text: `${nItem(e.itemCodigo)} · ${e.itemDescricao}` }),
        `${e.ordem}. ${e.rotaNome}${e.retrabalho ? ' (retrabalho)' : ''}`,
        el('span', { class: e.atrasado ? 'texto-perigo seminegrito' : 'texto-mudo', text: e.prazoEntrega ? diaMes(e.prazoEntrega) : '—' }),
        e.dataInicio ? status('Em execução') : status('Pronta para iniciar'),
      ],
    }));
  } catch (e) {
    mostrarErro(e);
  }
}
