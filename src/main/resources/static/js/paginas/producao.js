// Produção (INCLUÍDO em 21/10/2026), no layout do producao.tsx do protótipo.
// O protótipo registrava "quantidade produzida e defeitos", que não existem no banco. No modelo do grupo, a produção
// é a execução das etapas da rota de cada item (tabela ItensRotasProcessos): iniciar (com operador) e concluir, e o
// retrabalho é uma nova etapa igual (RN-ITE-06). Por isso a tela mostra a fila de etapas e o indicador de retrabalho
// do PDCA (% de itens executados com alguma etapa repetida).

import { get, post } from '../api.js';
import { data, dataHora, hojeISO, hora, nItem, nOs, normalizar, percentualRetrabalho } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  abas, botao, cabecalho, campo, campoBusca, cartao, dialogo, el, entrada, esqueleto, indicador, interruptor,
  mostrarErro, ocupado, preencherSelecao, selecao, status, sucesso, tabela, trocar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'producao', titulo: 'Produção' });

if (pagina) {
  const { conteudo, usuario } = pagina;
  const k = {
    execucao: indicador({ titulo: 'Em execução', nomeIcone: 'factory', destaque: 'primary', dica: 'etapas iniciadas' }),
    aIniciar: indicador({ titulo: 'A iniciar', nomeIcone: 'list-ordered', destaque: 'info', dica: 'etapas na fila' }),
    hoje: indicador({ titulo: 'Concluídas hoje', nomeIcone: 'circle-check', destaque: 'success', dica: 'etapas' }),
    retrabalho: indicador({ titulo: 'Retrabalho no mês', nomeIcone: 'rotate-ccw', destaque: 'warning', dica: 'itens executados' }),
  };
  const filtros = { busca: '', etapa: '', soProntas: false };
  let fila = [];

  // ---------------------------------------------------------------- aba Fila
  const areaFila = el('div', {}, esqueleto(5));
  const filtroEtapa = selecao([], { vazio: 'Todas as etapas', 'aria-label': 'Etapa' });
  filtroEtapa.classList.add('largura-filtro');
  filtroEtapa.addEventListener('change', () => { filtros.etapa = filtroEtapa.value; desenharFila(); });
  const abaFila = el('div', { class: 'pilha-4' },
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' },
      campoBusca('Buscar OS, item ou cliente...', (v) => { filtros.busca = v; desenharFila(); }, { classe: 'flex-1' }),
      filtroEtapa,
      interruptor('Só as prontas para iniciar', { aoMudar: (v) => { filtros.soProntas = v; desenharFila(); } }))),
    el('div', { class: 'card card-tabela' }, areaFila));

  function desenharFila() {
    const termo = normalizar(filtros.busca);
    const lista = fila.filter((e) => {
      if (filtros.etapa && e.rotaNome !== filtros.etapa) return false;
      if (filtros.soProntas && !(e.podeIniciar && !e.dataInicio)) return false;
      if (termo && !normalizar(`os-${e.osNumero} os ${e.osNumero} #${e.itemCodigo} ${e.itemDescricao} ${e.clienteNome}`).includes(termo)) return false;
      return true;
    });
    trocar(areaFila, tabela({
      colunas: [{ titulo: 'OS' }, { titulo: 'Item' }, { titulo: 'Etapa' }, { titulo: 'Cliente' }, { titulo: 'Prazo' }, { titulo: 'Situação' }, { titulo: 'Ações', classe: 'direita' }],
      linhas: lista,
      textoVazio: fila.length ? 'Nenhuma etapa com esses filtros.' : 'Nenhuma etapa pendente: a produção está em dia.',
      classeLinha: (e) => (e.atrasado ? 'linha-atrasada' : ''),
      montar: (e) => {
        let situacao;
        if (e.dataInicio) situacao = el('div', { class: 'coluna' }, status('Em execução'), el('span', { class: 'texto-xs texto-mudo', text: `desde ${hora(e.dataInicio)} (${data(e.dataInicio)}) · ${e.funcionarioNome || '—'}` }));
        else if (e.podeIniciar) situacao = status('Pronta para iniciar');
        else situacao = el('span', { class: 'texto-xs texto-mudo', text: e.motivo });
        const acoes = [];
        if (!e.dataInicio && e.podeIniciar) acoes.push(botao('Iniciar', { tamanho: 'sm', variante: 'primario', icone: 'play', onclick: () => dialogoIniciar(e).catch(mostrarErro) }));
        if (e.dataInicio) {
          const b = botao('Concluir', { tamanho: 'sm', variante: 'primario', icone: 'check' });
          b.addEventListener('click', () => ocupado(b, async () => {
            try {
              await post(`/etapas-item/${e.etapaCodigo}/concluir`);
              sucesso(`${e.rotaNome} do item ${nItem(e.itemCodigo)} concluída.`);
              await carregar();
            } catch (erro) {
              mostrarErro(erro);
            }
          }));
          acoes.push(b);
        }
        return [
          el('a', { class: 'mono seminegrito texto-primario', href: `ordem.html?numero=${e.osNumero}`, text: nOs(e.osNumero) }),
          el('a', { href: `peca.html?codigo=${e.itemCodigo}` }, el('span', { class: 'mono texto-primario', text: nItem(e.itemCodigo) }), ` ${e.itemDescricao}`),
          el('span', { class: 'linha' }, `${e.ordem}. ${e.rotaNome}`, e.retrabalho ? status('Retrabalho') : null),
          el('span', { class: 'texto-mudo', text: e.clienteNome }),
          el('span', { class: e.atrasado ? 'texto-perigo seminegrito' : 'texto-mudo', text: e.prazoEntrega ? data(e.prazoEntrega) : '—' }),
          situacao,
          { conteudo: el('div', { class: 'acoes' }, acoes) },
        ];
      },
    }));
  }

  async function dialogoIniciar(e) {
    const funcionarios = await ref.funcionarios();
    const s = selecao(funcionarios.map((f) => ({ valor: f.codigo, rotulo: `${f.nome} · ${f.cargo}` })), { vazio: 'Selecione o operador', valor: usuario.funcionarioCodigo });
    const iniciar = botao('Iniciar etapa', { variante: 'primario', icone: 'play' });
    const dlg = dialogo({
      titulo: `Iniciar ${e.rotaNome} — item ${nItem(e.itemCodigo)}`,
      descricao: `${nOs(e.osNumero)} · ${e.itemDescricao}. A hora de início é a do servidor.`,
      conteudo: campo('Operador', s, { obrigatorio: true }),
      botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), iniciar],
    });
    iniciar.addEventListener('click', () => ocupado(iniciar, async () => {
      try {
        await post(`/etapas-item/${e.etapaCodigo}/iniciar`, { funcionarioCodigo: Number(s.value) || null });
        dlg.fechar();
        sucesso(`${e.rotaNome} iniciada.`);
        await carregar();
      } catch (erro) {
        dlg.mostrarErro(erro);
      }
    }));
  }

  // ---------------------------------------------------------------- aba Retrabalhos (indicador do PDCA)
  const inicio = entrada({ type: 'date', value: hojeISO().slice(0, 8) + '01' });
  const fim = entrada({ type: 'date', value: hojeISO() });
  const areaIndicador = el('div', { class: 'pilha-4' });
  const atualizar = botao('Atualizar', { icone: 'refresh-cw' });
  atualizar.addEventListener('click', () => ocupado(atualizar, carregarRetrabalho));
  const abaRetrabalho = el('div', { class: 'pilha-4' },
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' }, campo('De', inicio), campo('Até', fim), el('div', { class: 'campo' }, el('span', { class: 'rotulo', text: ' ' }), atualizar))),
    areaIndicador);

  async function carregarRetrabalho() {
    trocar(areaIndicador, esqueleto(3));
    try {
      const q = `?inicio=${inicio.value}&fim=${fim.value}`;
      const [ind, itens] = await Promise.all([get('/indicadores/retrabalho' + q), get('/indicadores/retrabalho/itens' + q)]);
      trocar(areaIndicador,
        el('div', { class: 'grade sm-3' },
          cartao({ titulo: '% de retrabalho', classe: 'progresso brilho', corpo: [el('div', { class: 'numero-grande', text: percentualRetrabalho(ind) }), el('p', { class: 'texto-xs texto-mudo mt-1', text: `${data(ind.inicio)} a ${data(ind.fim)}` })] }),
          cartao({ titulo: 'Itens executados', corpo: el('div', { class: 'numero-grande texto-info', text: String(ind.itensExecutados) }) }),
          cartao({ titulo: 'Com etapa repetida', corpo: el('div', { class: 'numero-grande texto-alerta', text: String(ind.itensComRetrabalho) }) })),
        el('p', { class: 'texto-xs texto-mudo', text: '% de retrabalho = itens com alguma etapa repetida ÷ itens executados no período × 100 (indicador do PDCA).' }),
        el('div', { class: 'card card-tabela' }, tabela({
          colunas: [{ titulo: 'Item' }, { titulo: 'OS' }, { titulo: 'Cliente' }, { titulo: 'Finalização' }, { titulo: 'Etapas repetidas' }],
          linhas: itens,
          textoVazio: 'Nenhum item com retrabalho no período.',
          aoClicar: (i) => { location.href = `peca.html?codigo=${i.itemCodigo}`; },
          montar: (i) => [
            el('span', {}, el('span', { class: 'mono texto-primario seminegrito', text: nItem(i.itemCodigo) }), ` ${i.descricao}`),
            el('span', { class: 'mono', text: nOs(i.osNumero) }),
            i.clienteNome,
            el('span', { class: 'texto-mudo', text: dataHora(i.dataFinalizacao) }),
            i.etapasRepetidas.length ? el('span', { class: 'linha' }, i.etapasRepetidas.map((n) => el('span', { class: 'badge badge-primario', text: n }))) : '—',
          ],
        })));
    } catch (e) {
      trocar(areaIndicador);
      mostrarErro(e);
    }
  }

  // ---------------------------------------------------------------- registrar retrabalho (botão do topo)
  async function dialogoRetrabalho() {
    const itens = (await get('/itens')).filter((i) => i.status !== 'Cancelada' && (i.etapas || []).some((e) => e.dataConclusao));
    const sItem = selecao(itens.map((i) => ({ valor: i.codigo, rotulo: `${nItem(i.codigo)} · ${nOs(i.osNumero)} · ${i.descricao}` })), { vazio: itens.length ? 'Selecione o item' : 'Nenhum item com etapa concluída' });
    const sEtapa = selecao([], { vazio: 'Escolha o item antes', disabled: true });
    sItem.addEventListener('change', () => {
      const item = itens.find((i) => String(i.codigo) === sItem.value);
      const concluidas = (item?.etapas || []).filter((e) => e.dataConclusao);
      preencherSelecao(sEtapa, concluidas.map((e) => ({ valor: e.codigo, rotulo: `${e.ordem}. ${e.rotaNome} · concluída em ${dataHora(e.dataConclusao)}` })), { vazio: 'Selecione a etapa concluída' });
      sEtapa.disabled = !concluidas.length;
    });
    const registrar = botao('Registrar retrabalho', { variante: 'primario', icone: 'rotate-ccw' });
    const dlg = dialogo({
      titulo: 'Registrar retrabalho',
      descricao: 'A etapa concluída não é reaberta: uma nova etapa igual entra no fim da rota do item e conta no indicador de retrabalho.',
      conteudo: el('div', { class: 'pilha-4' }, campo('Item', sItem, { obrigatorio: true }), campo('Etapa a refazer', sEtapa, { obrigatorio: true })),
      botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), registrar],
    });
    registrar.addEventListener('click', () => ocupado(registrar, async () => {
      if (!sEtapa.value) { dlg.mostrarErro({ mensagens: ['Selecione o item e a etapa concluída.'] }); return; }
      try {
        await post(`/etapas-item/${sEtapa.value}/retrabalho`);
        dlg.fechar();
        sucesso('Retrabalho registrado: a nova etapa entrou na fila.');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  // ---------------------------------------------------------------- montagem e carga
  const controle = abas([
    { id: 'fila', rotulo: 'Fila de produção', icone: 'list-ordered', conteudo: abaFila },
    { id: 'retrabalho', rotulo: 'Retrabalhos', icone: 'rotate-ccw', conteudo: () => { carregarRetrabalho(); return abaRetrabalho; } },
  ]);
  const botaoRetrabalho = botao('Registrar retrabalho', { variante: 'primario', icone: 'plus', onclick: () => dialogoRetrabalho().catch(mostrarErro) });
  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Produção', 'Execução das etapas, retrabalhos e indicador de qualidade.', [botaoRetrabalho]),
    el('div', { class: 'grade sm-2 lg-4' }, Object.values(k)),
    controle.elemento));

  async function carregar() {
    try {
      const [lista, resumo, etapas] = await Promise.all([get('/etapas-item/fila'), get('/dashboard/resumo'), ref.etapas()]);
      fila = lista;
      const r = resumo.retrabalhoMes || {};
      k.execucao.definir(resumo.etapasEmExecucao);
      k.aIniciar.definir(resumo.etapasAIniciar, `${lista.filter((e) => e.podeIniciar && !e.dataInicio).length} prontas para iniciar`);
      k.hoje.definir(resumo.etapasConcluidasHoje);
      k.retrabalho.definir(percentualRetrabalho(r), r.itensExecutados > 0
        ? `${r.itensComRetrabalho} de ${r.itensExecutados} itens executados` : 'nenhum item executado no mês');
      preencherSelecao(filtroEtapa, etapas.map((e) => e.nome), { vazio: 'Todas as etapas', valor: filtros.etapa });
      desenharFila();
    } catch (e) {
      trocar(areaFila);
      mostrarErro(e);
    }
  }

  carregar();
}
