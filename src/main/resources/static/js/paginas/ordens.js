// Ordens de Serviço — lista (INCLUÍDO em 21/10/2026), no layout do ordens.tsx do protótipo.
// Dados: GET /api/ordens. Filtros na tela: busca (nº ou cliente), status, cliente e "só com itens atrasados".
// ALTERADO (18/11/2026) [E3-32]: os dados passaram a vir de GET /api/ordens/painel, a view dbo.vw_painel_ordens_servico
// da Etapa 3: uma linha por OS, com status atual e as contagens de itens já feitas no banco (antes a tela recebia
// cada OS com todos os itens, rotas e fotos só para contar). A coluna "Prazo" (calculada em JS a partir dos itens)
// virou "Peças": executadas/total, mais as atrasadas, que é o que a view entrega.

import { get } from '../api.js';
import { data, moeda, nOs, normalizar } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
// ALTERADO (18/11/2026) [E3-32]: prazoDaOs saiu do import (precisava da lista de itens, que o painel não traz).
import { osAtrasada } from '../componentes/os.js';
import {
  badge, cabecalho, campoBusca, el, esqueleto, icone, interruptor, mostrarErro, parametroUrl, preencherSelecao,
  prioridade, selecao, status, tabela, trocar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'ordens', titulo: 'Ordens de Serviço' });

if (pagina) {
  const { conteudo } = pagina;
  const filtros = { busca: parametroUrl('busca') || '', status: parametroUrl('status') || '', cliente: '', atrasadas: false };
  let ordens = [];

  const busca = campoBusca('Buscar OS (nº) ou cliente...', (v) => { filtros.busca = v; desenhar(); }, { valor: filtros.busca, classe: 'flex-1' });
  const filtroStatus = selecao([], { vazio: 'Todos os status', 'aria-label': 'Status' });
  filtroStatus.addEventListener('change', () => { filtros.status = filtroStatus.value; desenhar(); });
  const filtroCliente = selecao([], { vazio: 'Todos os clientes', 'aria-label': 'Cliente' });
  filtroCliente.addEventListener('change', () => { filtros.cliente = filtroCliente.value; desenhar(); });
  const soAtrasadas = interruptor('Só com itens atrasados', { aoMudar: (v) => { filtros.atrasadas = v; desenhar(); } });
  const limpar = el('button', { type: 'button', class: 'btn btn-contorno btn-icone', title: 'Limpar filtros', 'aria-label': 'Limpar filtros', onclick: () => {
    filtros.busca = ''; filtros.status = ''; filtros.cliente = ''; filtros.atrasadas = false;
    busca.entrada.value = ''; filtroStatus.value = ''; filtroCliente.value = ''; soAtrasadas.caixa.checked = false;
    desenhar();
  } }, icone('filter'));

  const areaTabela = el('div', {}, esqueleto(6));
  const resumo = el('p', { class: 'texto-xs texto-mudo' });
  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Ordens de Serviço', 'Gerencie todas as OS da operação.', [
      el('a', { class: 'btn btn-primario', href: 'ordem-nova.html' }, icone('plus'), el('span', { text: 'Nova OS' })),
    ]),
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' }, busca, filtroStatus, filtroCliente, soAtrasadas, limpar)),
    el('div', { class: 'card card-tabela' }, areaTabela),
    resumo));
  [filtroStatus, filtroCliente].forEach((s) => s.classList.add('largura-filtro'));

  function filtrar() {
    const termo = normalizar(filtros.busca).replace(/^os[\s-]*/, '');
    return ordens.filter((o) => {
      if (filtros.status && o.status !== filtros.status) return false;
      if (filtros.cliente && String(o.clienteCodigo) !== filtros.cliente) return false;
      if (filtros.atrasadas && !osAtrasada(o)) return false;
      if (termo && !(String(o.numero) === termo || normalizar(o.clienteNome).includes(termo))) return false;
      return true;
    });
  }

  function desenhar() {
    const lista = filtrar();
    resumo.textContent = `${lista.length} de ${ordens.length} OS`;
    trocar(areaTabela, tabela({
      colunas: [
        { titulo: 'Nº OS' }, { titulo: 'Cliente' }, { titulo: 'Status' }, { titulo: 'Prioridade' }, { titulo: 'Criado por' },
        { titulo: 'Abertura' }, { titulo: 'Peças' }, { titulo: 'Valor total', classe: 'direita' }, { titulo: 'Ações', classe: 'direita' },
      ],
      linhas: lista,
      textoVazio: ordens.length ? 'Nenhuma OS com esses filtros.' : 'Nenhuma OS cadastrada. Use "Nova OS" para abrir a primeira.',
      aoClicar: (o) => { location.href = `ordem.html?numero=${o.numero}`; },
      classeLinha: (o) => (osAtrasada(o) && o.status !== 'Concluída' && o.status !== 'Cancelada' ? 'linha-atrasada' : ''),
      montar: (o) => {
        // INCLUÍDO (18/11/2026) [E3-32]: contagens da view (itens cancelados ficam fora do total, como no progresso da OS).
        const validos = o.qtdItens - o.qtdItensCancelados;
        const pecas = el('div', { class: 'coluna' },
          el('span', { class: 'texto-mudo', text: `${o.qtdItensExecutados}/${validos} executada(s)` }),
          o.qtdItensAtrasados ? el('span', { class: 'texto-xs texto-perigo seminegrito', text: `${o.qtdItensAtrasados} atrasada(s)` }) : null);
        return [
          el('span', { class: 'mono seminegrito texto-primario', text: nOs(o.numero) }),
          o.clienteNome,
          el('span', { class: 'linha' }, status(o.status), osAtrasada(o) ? status('Atrasada') : null),
          prioridade(o.prioridade),
          el('span', { class: 'texto-mudo', text: o.registradaPor || '—' }),
          el('span', { class: 'texto-mudo', text: data(o.dataEntrada) }),
          pecas,
          { conteudo: el('div', { class: 'coluna' }, el('span', { class: 'seminegrito', text: moeda(o.valorOs) }),
            o.valorIncompleto ? el('span', { class: 'ml-auto' }, badge('valor incompleto', 'alerta')) : null) },
          { conteudo: el('div', { class: 'acoes' }, el('a', { class: 'btn btn-fantasma btn-icone-sm', href: `ordem.html?numero=${o.numero}`, title: 'Ver OS', 'aria-label': `Ver ${nOs(o.numero)}` }, icone('eye'))) },
        ];
      },
    }));
  }

  try {
    // ALTERADO (18/11/2026) [E3-32]: era get('/ordens').
    const [lista, statusOs] = await Promise.all([get('/ordens/painel'), ref.statusOs()]);
    ordens = lista.sort((a, b) => b.numero - a.numero);
    preencherSelecao(filtroStatus, statusOs.map((s) => s.nome), { vazio: 'Todos os status', valor: filtros.status });
    const clientes = [...new Map(ordens.map((o) => [o.clienteCodigo, o.clienteNome])).entries()].sort((a, b) => a[1].localeCompare(b[1]));
    preencherSelecao(filtroCliente, clientes.map(([c, n]) => ({ valor: c, rotulo: n })), { vazio: 'Todos os clientes' });
    desenhar();
  } catch (e) {
    trocar(areaTabela, el('div', { class: 'vazio' }, icone('circle-alert'), el('p', { text: 'Não foi possível carregar as OS.' })));
    mostrarErro(e);
  }
}
