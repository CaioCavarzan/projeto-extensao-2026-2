// Gráficos do painel inicial (INCLUÍDO em 21/10/2026), com o Chart.js 4 (vendor/chartjs, licença MIT).
//
// Cada gráfico busca GET /api/dashboard/graficos/{nome} e desenha a resposta GraficoResponse:
//   { nome, titulo, tipo: 'barra' | 'barra-horizontal' | 'linha' | 'rosca', rotulos: [...],
//     series: [{ nome, valores: [...] }], disponivel, mensagem }
// Enquanto o back-end responder disponivel = false (dados a desenvolver, ver DashboardService), aparece o quadro
// "Em desenvolvimento" com a mensagem da API. Quando a consulta for programada, o gráfico aparece sozinho.
// As cores são as do tema (chart-1 a chart-5) convertidas de oklch para hexadecimal, porque o Chart.js mexe nas
// cores (hover) com uma biblioteca que não entende oklch.

import { get } from './api.js';
import { cartao, el, esqueleto, icone, trocar } from './ui.js';

const CORES = ['#f51d31', '#0099e6', '#ecaa0b', '#00ac4e', '#b37bff'];
const GRADE = '#2f2726';
const TEXTO = '#a49493';
const FUNDO_CARD = '#1e1616';
const FRENTE = '#f8f4f4';
const TIPOS = { barra: 'bar', 'barra-horizontal': 'bar', linha: 'line', rosca: 'doughnut' };

function quadroEspera(mensagem, titulo = 'Em desenvolvimento') {
  return el('div', { class: 'grafico-espera' }, icone('chart-column'), el('strong', { text: titulo }), el('span', { text: mensagem || '' }));
}

/** Configuração do Chart.js para uma GraficoResponse. Exportada para os testes da tela. */
export function configuracaoGrafico(dados) {
  const tipo = TIPOS[dados.tipo] || 'bar';
  const rosca = tipo === 'doughnut';
  const datasets = (dados.series || []).map((s, i) => {
    const cor = CORES[i % CORES.length];
    const base = { label: s.nome, data: (s.valores || []).map((v) => (v === null ? null : Number(v))) };
    if (rosca) return { ...base, backgroundColor: (dados.rotulos || []).map((_, j) => CORES[j % CORES.length]), borderColor: FUNDO_CARD, borderWidth: 2 };
    if (tipo === 'line') {
      return { ...base, borderColor: cor, backgroundColor: cor + '33', fill: true, tension: 0.35, borderWidth: 3, pointBackgroundColor: cor, pointRadius: 4, pointHoverRadius: 6 };
    }
    return { ...base, backgroundColor: cor, borderRadius: 6, maxBarThickness: 48 };
  });
  const eixo = { grid: { color: GRADE, borderDash: [3, 3] }, border: { color: GRADE }, ticks: { color: TEXTO, font: { size: 12 } } };
  return {
    type: tipo,
    data: { labels: dados.rotulos || [], datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: dados.tipo === 'barra-horizontal' ? 'y' : 'x',
      plugins: {
        legend: { display: rosca || datasets.length > 1, labels: { color: TEXTO, boxWidth: 12, font: { size: 12 } } },
        tooltip: { backgroundColor: FUNDO_CARD, borderColor: GRADE, borderWidth: 1, titleColor: FRENTE, bodyColor: FRENTE, cornerRadius: 8, padding: 10 },
      },
      scales: rosca ? {} : { x: eixo, y: { ...eixo, beginAtZero: true } },
    },
  };
}

async function carregarGrafico(caixa, def) {
  try {
    const dados = await get(`/dashboard/graficos/${def.nome}`);
    if (!dados.disponivel) {
      trocar(caixa, quadroEspera(dados.mensagem));
      return;
    }
    if (!window.Chart) {
      trocar(caixa, quadroEspera('A biblioteca de gráficos (vendor/chartjs) não foi carregada.', 'Gráfico indisponível'));
      return;
    }
    if (!dados.rotulos?.length) {
      trocar(caixa, quadroEspera('Ainda não há dados no período.', 'Sem dados'));
      return;
    }
    const canvas = el('canvas', { role: 'img', 'aria-label': dados.titulo || def.titulo });
    trocar(caixa, canvas);
    window.Chart.defaults.font.family = "'Inter Variable', Inter, 'Segoe UI', sans-serif";
    window.Chart.defaults.color = TEXTO;
    new window.Chart(canvas, configuracaoGrafico(dados));
  } catch (e) {
    trocar(caixa, quadroEspera((e?.mensagens || [e?.message]).join(' '), 'Não foi possível carregar'));
  }
}

/** Card do gráfico (título, descrição e área do desenho). def: { nome, titulo, descricao } de config.js. */
export function cartaoGrafico(def, { classe = '' } = {}) {
  const caixa = el('div', { class: 'grafico-caixa', 'data-grafico': def.nome }, esqueleto(1, 'esqueleto-bloco'));
  const card = cartao({ titulo: def.titulo, descricao: def.descricao, corpo: caixa, classe });
  carregarGrafico(caixa, def);
  return card;
}
