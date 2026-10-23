// Cronograma (INCLUÍDO em 21/10/2026), no layout do cronograma.tsx do protótipo: calendário do mês, fila do dia
// e próximos planejamentos.
// Modelo do grupo: um cronograma por dia (Cronogramas) com uma fila de etapas (CronogramasItens): cada linha é uma
// etapa da rota de um item, com quantidade planejada e posição na fila. Regras: gravado com pelo menos uma etapa
// (RN-CRO-01); um por dia; dia que já passou não é planejado nem alterado; só entram etapas não concluídas de itens
// não encerrados; a quantidade não passa do que falta planejar; a fila é renumerada a cada mudança; "Sugerir fila"
// ordena pela pontuação (score do item) e pelo prazo.

import { del, get, post, put } from '../api.js';
import { MESES, MESES_CURTOS, data, decimalParaCampo, diaMes, hojeISO, isoDe, lerDecimal, nItem, nOs, numero, quantidade, textoOuNulo } from '../formato.js';
import { montarPagina } from '../layout.js';
import {
  areaTexto, botao, botaoIcone, cabecalho, campo, cartao, confirmar, dialogo, el, entrada, esqueleto, icone, mostrarErro,
  ocupado, parametroUrl, selecao, sucesso, trocar, vazio,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'cronograma', titulo: 'Cronograma' });

if (pagina) {
  const { conteudo } = pagina;
  const hoje = hojeISO();
  let cronogramas = new Map(); // data ISO -> CronogramaResponse
  let diaSelecionado = parametroUrl('data') || hoje;
  const mes = new Date(Number(diaSelecionado.slice(0, 4)), Number(diaSelecionado.slice(5, 7)) - 1, 1);

  const tituloMes = el('h3', { class: 'card-titulo' });
  const calendario = el('div', { class: 'calendario' });
  const areaDia = el('div', {}, esqueleto(4));
  const areaProximos = el('div', { class: 'pilha-2' });

  const navMes = (delta) => { mes.setMonth(mes.getMonth() + delta); desenharCalendario(); };
  const cardCalendario = el('div', { class: 'card' },
    el('div', { class: 'card-cab em-linha' }, tituloMes, el('div', { class: 'linha' },
      botao('Hoje', { tamanho: 'sm', variante: 'fantasma', onclick: () => { const d = new Date(); mes.setFullYear(d.getFullYear(), d.getMonth(), 1); selecionar(hoje); } }),
      botaoIcone('chevron-left', 'Mês anterior', () => navMes(-1), { tamanho: 'icone' }),
      botaoIcone('chevron-right', 'Próximo mês', () => navMes(1), { tamanho: 'icone' }))),
    el('div', { class: 'card-corpo' },
      el('div', { class: 'calendario-semana' }, ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'].map((d) => el('div', { text: d }))),
      calendario));

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Cronograma', 'Planejamento diário da produção: uma fila de etapas por dia.', [
      botao('Novo planejamento', { variante: 'primario', icone: 'plus', onclick: () => dialogoNovo(diaSelecionado >= hoje ? diaSelecionado : hoje).catch(mostrarErro) }),
    ]),
    cardCalendario,
    areaDia,
    cartao({ titulo: 'Próximos planejamentos', descricao: 'Hoje e os próximos 7 dias', corpo: areaProximos })));

  // ---------------------------------------------------------------- calendário
  function desenharCalendario() {
    tituloMes.textContent = `${MESES[mes.getMonth()]} ${mes.getFullYear()}`;
    trocar(calendario);
    const primeiroDiaSemana = mes.getDay();
    const diasNoMes = new Date(mes.getFullYear(), mes.getMonth() + 1, 0).getDate();
    for (let i = 0; i < primeiroDiaSemana; i++) calendario.append(el('div', { class: 'dia', 'aria-hidden': 'true' }));
    for (let d = 1; d <= diasNoMes; d++) {
      const iso = isoDe(new Date(mes.getFullYear(), mes.getMonth(), d));
      const c = cronogramas.get(iso);
      const classes = ['dia', 'valido'];
      if (iso === hoje) classes.push('hoje');
      if (iso < hoje) classes.push('passado');
      if (iso === diaSelecionado) classes.push('selecionado');
      const celula = el('button', { type: 'button', class: classes.join(' '), 'aria-label': `${d} de ${MESES[mes.getMonth()]}${c ? `, ${c.fila.length} etapa(s) planejada(s)` : ''}`, onclick: () => selecionar(iso) },
        el('div', { class: 'dia-numero', text: String(d) }));
      if (c) {
        c.fila.slice(0, 2).forEach((e) => celula.append(el('div', { class: 'dia-evento' },
          el('span', { class: 'mono', text: `${nOs(e.osNumero)} · ${nItem(e.itemCodigo)}` }), el('div', { text: `${e.rotaNome} · ${numero(e.qtdPlanejado)}x` }))));
        if (c.fila.length > 2) celula.append(el('div', { class: 'texto-10 texto-mudo mt-1', text: `+${c.fila.length - 2} etapa(s)` }));
        celula.append(el('span', { class: 'dia-total', text: `${c.fila.length} et.` }));
      }
      calendario.append(celula);
    }
  }

  function selecionar(iso) {
    diaSelecionado = iso;
    if (Number(iso.slice(5, 7)) - 1 !== mes.getMonth() || Number(iso.slice(0, 4)) !== mes.getFullYear()) {
      mes.setFullYear(Number(iso.slice(0, 4)), Number(iso.slice(5, 7)) - 1, 1);
    }
    desenharCalendario();
    desenharDia();
    history.replaceState(null, '', `cronograma.html?data=${iso}`);
  }

  // ---------------------------------------------------------------- fila do dia
  function desenharDia() {
    const c = cronogramas.get(diaSelecionado);
    const passado = diaSelecionado < hoje;
    const titulo = `Fila do dia ${data(diaSelecionado)}${diaSelecionado === hoje ? ' (hoje)' : ''}`;
    if (!c) {
      trocar(areaDia, cartao({
        titulo, corpo: el('div', { class: 'vazio' }, icone('calendar-days'),
          el('p', { text: passado ? 'Sem planejamento neste dia.' : 'Nenhum planejamento para este dia.' }),
          passado ? null : botao('Planejar este dia', { variante: 'primario', icone: 'plus', onclick: () => dialogoNovo(diaSelecionado).catch(mostrarErro) })),
      }));
      return;
    }
    const acoes = [];
    if (!passado) {
      acoes.push(acaoBotao('Sugerir fila', 'sparkles', () => post(`/cronogramas/${c.codigo}/fila/sugerir`), 'Fila ordenada pela pontuação e pelo prazo.'));
      acoes.push(botao('Incluir etapa', { tamanho: 'sm', icone: 'plus', onclick: () => dialogoIncluir(c).catch(mostrarErro) }));
      acoes.push(botao('Editar', { tamanho: 'sm', icone: 'pencil', onclick: () => dialogoEditar(c) }));
      const excluir = botao('Excluir', { tamanho: 'sm', variante: 'perigo-contorno', icone: 'trash-2' });
      excluir.addEventListener('click', async () => {
        if (!(await confirmar(`Excluir o cronograma de ${data(c.dataPlanejada)} com ${c.fila.length} etapa(s)? As etapas continuam nos itens; só o planejamento é apagado.`, { titulo: 'Excluir cronograma', textoConfirmar: 'Excluir', perigo: true }))) return;
        await executar(excluir, () => del(`/cronogramas/${c.codigo}`), 'Cronograma excluído.');
      });
      acoes.push(excluir);
    }
    const linhas = c.fila.map((e, i) => {
      const botoes = passado ? [] : [
        botaoIcone('chevron-up', 'Subir na fila', (ev) => mover(ev.currentTarget, c, i, -1), { disabled: i === 0 }),
        botaoIcone('chevron-down', 'Descer na fila', (ev) => mover(ev.currentTarget, c, i, 1), { disabled: i === c.fila.length - 1 }),
        botaoIcone('x', 'Tirar do cronograma', async (ev) => {
          const b = ev.currentTarget;
          if (!(await confirmar(`Tirar ${e.rotaNome} do item ${nItem(e.itemCodigo)} do cronograma?`, { titulo: 'Tirar etapa', textoConfirmar: 'Tirar', perigo: true }))) return;
          await executar(b, () => del(`/cronogramas/${c.codigo}/etapas/${e.etapaCodigo}`), 'Etapa tirada do cronograma.');
        }),
      ];
      return el('div', { class: 'item-lista claro' },
        el('div', { class: 'linha-4 flex-1' },
          el('span', { class: 'posicao', text: String(e.posicao + 1), title: `Posição ${e.posicao + 1} na fila` }),
          el('div', { class: 'flex-1' },
            el('p', {}, el('a', { class: 'mono seminegrito texto-primario', href: `peca.html?codigo=${e.itemCodigo}`, text: `${nOs(e.osNumero)} · ${nItem(e.itemCodigo)}` }), ` ${e.itemDescricao}`),
            el('p', { class: 'texto-xs texto-mudo', text: `Etapa ${e.ordem}. ${e.rotaNome} · planejado ${numero(e.qtdPlanejado)} · prazo ${e.prazoEntrega ? data(e.prazoEntrega) : '—'} · pontuação ${e.pontuacao != null ? numero(e.pontuacao) : '—'}` }))),
        el('div', { class: 'linha' }, botoes));
    });
    trocar(areaDia, cartao({
      titulo, descricao: `Registrado por ${c.registradoPor || '—'}${c.observacao ? ` · ${c.observacao}` : ''}${passado ? ' · dia encerrado: só consulta' : ''}`,
      acoes: acoes.length ? acoes : null,
      corpo: el('div', { class: 'pilha-2' }, linhas),
    }));
  }

  async function executar(botaoEl, chamada, mensagem) {
    await ocupado(botaoEl, async () => {
      try {
        await chamada();
        sucesso(mensagem);
        await carregar();
      } catch (e) {
        mostrarErro(e);
      }
    });
  }

  function acaoBotao(texto, nomeIcone, chamada, mensagem) {
    const b = botao(texto, { tamanho: 'sm', icone: nomeIcone });
    b.addEventListener('click', () => executar(b, chamada, mensagem));
    return b;
  }

  function mover(botaoEl, c, indice, delta) {
    const ordem = c.fila.map((e) => e.etapaCodigo);
    const [x] = ordem.splice(indice, 1);
    ordem.splice(indice + delta, 0, x);
    return executar(botaoEl, () => put(`/cronogramas/${c.codigo}/fila`, ordem), 'Fila reordenada.');
  }

  // ---------------------------------------------------------------- diálogos
  /** Etapas que podem entrar num cronograma: não concluídas, de itens não executados nem cancelados. */
  async function etapasPlanejaveis(excluir = new Set()) {
    const fila = await get('/etapas-item/fila');
    return fila.filter((e) => e.itemStatus !== 'Executada' && e.itemStatus !== 'Cancelada' && !excluir.has(e.etapaCodigo));
  }

  function linhaEtapa(e, marcada = false) {
    const caixa = el('input', { type: 'checkbox', checked: marcada });
    const qtd = entrada({ value: decimalParaCampo(e.qtdTotal), inputmode: 'decimal', 'aria-label': 'Quantidade planejada', class: 'input' });
    qtd.classList.add('largura-filtro');
    const linha = el('div', { class: 'item-lista claro' },
      el('label', { class: 'opcao flex-1' }, caixa, el('span', {},
        el('span', { class: 'mono seminegrito texto-primario', text: `${nOs(e.osNumero)} · ${nItem(e.itemCodigo)}` }),
        ` ${e.itemDescricao} — ${e.ordem}. ${e.rotaNome}`,
        el('span', { class: 'texto-xs texto-mudo', text: ` · prazo ${e.prazoEntrega ? diaMes(e.prazoEntrega) : '—'} · total ${quantidade(e.qtdTotal, e.unidade)}${e.dataInicio ? ' · em execução' : ''}` }))),
      el('div', { class: 'linha' }, el('span', { class: 'texto-xs texto-mudo', text: 'Qtd' }), qtd));
    linha.dados = () => (caixa.checked ? { etapaCodigo: e.etapaCodigo, qtdPlanejado: lerDecimal(qtd.value) } : null);
    return linha;
  }

  async function dialogoNovo(dataInicial) {
    const etapas = await etapasPlanejaveis();
    const dia = entrada({ type: 'date', value: dataInicial, min: hoje });
    const obs = areaTexto({ maxlength: 255, placeholder: 'Notas do planejamento...' });
    const linhas = etapas.map((e) => linhaEtapa(e));
    const salvar = botao('Salvar planejamento', { variante: 'primario', icone: 'save' });
    const dlg = dialogo({
      titulo: 'Novo planejamento', largura: '3xl',
      descricao: 'Escolha o dia e as etapas da fila. A quantidade planejada não pode passar do que falta produzir da etapa.',
      conteudo: el('div', { class: 'pilha-4' },
        el('div', { class: 'form-grade' },
          campo('Data de criação', entrada({ value: data(hoje), readOnly: true }), { largura: 'c3' }),
          campo('Data planejada de produção', dia, { largura: 'c3', obrigatorio: true }),
          campo('Observação', obs, { largura: 'c6' })),
        el('div', { class: 'campo' }, el('span', { class: 'rotulo', text: `Etapas pendentes (${etapas.length})` }),
          linhas.length ? el('div', { class: 'pilha-2' }, linhas) : vazio('Nenhuma etapa pendente para planejar.', 'list-checks'))),
      botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      const escolhidas = linhas.map((l) => l.dados()).filter(Boolean);
      if (escolhidas.some((x) => Number.isNaN(x.qtdPlanejado))) { dlg.mostrarErro({ mensagens: ['Quantidade planejada inválida: use números, com vírgula para decimais.'] }); return; }
      try {
        const c = await post('/cronogramas', { dataPlanejada: dia.value || null, observacao: textoOuNulo(obs.value), etapas: escolhidas });
        dlg.fechar();
        sucesso(`Cronograma de ${data(c.dataPlanejada)} criado com ${c.fila.length} etapa(s).`);
        diaSelecionado = c.dataPlanejada;
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function dialogoIncluir(c) {
    const etapas = await etapasPlanejaveis(new Set(c.fila.map((e) => e.etapaCodigo)));
    const s = selecao(etapas.map((e) => ({ valor: e.etapaCodigo, rotulo: `${nOs(e.osNumero)} · ${nItem(e.itemCodigo)} ${e.itemDescricao} — ${e.ordem}. ${e.rotaNome}` })), { vazio: etapas.length ? 'Selecione a etapa' : 'Nenhuma etapa disponível' });
    const qtd = entrada({ inputmode: 'decimal' });
    s.addEventListener('change', () => { const e = etapas.find((x) => String(x.etapaCodigo) === s.value); qtd.value = e ? decimalParaCampo(e.qtdTotal) : ''; });
    const salvar = botao('Incluir na fila', { variante: 'primario', icone: 'plus' });
    const dlg = dialogo({
      titulo: `Incluir etapa em ${data(c.dataPlanejada)}`, descricao: 'A etapa entra no fim da fila.',
      conteudo: el('div', { class: 'pilha-4' }, campo('Etapa', s, { obrigatorio: true }), campo('Quantidade planejada', qtd, { obrigatorio: true })),
      botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      const q = lerDecimal(qtd.value);
      if (Number.isNaN(q)) { dlg.mostrarErro({ mensagens: ['Quantidade planejada inválida.'] }); return; }
      try {
        await post(`/cronogramas/${c.codigo}/etapas`, { etapaCodigo: Number(s.value) || null, qtdPlanejado: q });
        dlg.fechar();
        sucesso('Etapa incluída na fila.');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  function dialogoEditar(c) {
    const dia = entrada({ type: 'date', value: c.dataPlanejada, min: hoje });
    const obs = areaTexto({ maxlength: 255, value: c.observacao || '' });
    const salvar = botao('Salvar', { variante: 'primario' });
    const dlg = dialogo({
      titulo: 'Editar cronograma', descricao: 'Mudar a data move a fila inteira para o outro dia (que não pode ter cronograma).',
      conteudo: el('div', { class: 'pilha-4' }, campo('Data planejada', dia, { obrigatorio: true }), campo('Observação', obs)),
      botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      try {
        const r = await put(`/cronogramas/${c.codigo}`, { dataPlanejada: dia.value || null, observacao: textoOuNulo(obs.value) });
        dlg.fechar();
        sucesso('Cronograma atualizado.');
        diaSelecionado = r.dataPlanejada;
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  // ---------------------------------------------------------------- próximos planejamentos
  function desenharProximos() {
    const limite = hojeISO(7);
    const proximos = [...cronogramas.values()].filter((c) => c.dataPlanejada >= hoje && c.dataPlanejada <= limite).sort((a, b) => a.dataPlanejada.localeCompare(b.dataPlanejada));
    trocar(areaProximos, proximos.length ? proximos.map((c) => el('button', { type: 'button', class: 'item-lista claro clicavel largura-total', onclick: () => selecionar(c.dataPlanejada) },
      el('div', { class: 'linha-4' },
        el('div', { class: 'data-ficha' }, el('span', { text: MESES_CURTOS[Number(c.dataPlanejada.slice(5, 7)) - 1] }), el('span', { text: String(Number(c.dataPlanejada.slice(8, 10))) })),
        el('div', { class: 'coluna', }, el('p', { class: 'seminegrito', text: `${c.fila.length} etapa(s) na fila` }),
          el('p', { class: 'texto-xs texto-mudo', text: c.fila.slice(0, 3).map((e) => `${nOs(e.osNumero)} ${e.rotaNome}`).join(' · ') + (c.fila.length > 3 ? ' ...' : '') }))),
      el('span', { class: 'texto-xs texto-mudo', text: `por ${c.registradoPor || '—'}` })))
      : el('p', { class: 'texto-sm texto-mudo', text: 'Nenhum planejamento para os próximos dias.' }));
  }

  async function carregar() {
    try {
      const lista = await get('/cronogramas');
      cronogramas = new Map(lista.map((c) => [c.dataPlanejada, c]));
      selecionar(diaSelecionado);
      desenharProximos();
    } catch (e) {
      trocar(areaDia, vazio('Não foi possível carregar o cronograma.', 'circle-alert'));
      mostrarErro(e);
    }
  }

  desenharCalendario();
  carregar();
}
