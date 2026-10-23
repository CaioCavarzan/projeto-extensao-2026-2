// Peças (itens das OS) — INCLUÍDO em 21/10/2026, no layout do pecas.tsx do protótipo (cards com foto).
// Dados: GET /api/itens e GET /api/ordens (cliente e prioridade de cada OS). A primeira foto de cada item é baixada
// só quando o card aparece na tela, porque a imagem exige login (ver api.urlDaFoto).

import { get, urlDaFoto } from '../api.js';
import { dialogoNovoItem, osEncerrada } from '../componentes/os.js';
import { data, nItem, nOs, normalizar, quantidade } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  botao, cabecalho, campoBusca, el, esqueleto, icone, interruptor, mostrarErro, parametroUrl, preencherSelecao,
  prioridade, selecao, status, trocar, vazio,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'pecas', titulo: 'Peças' });

if (pagina) {
  const { conteudo } = pagina;
  const filtros = { busca: '', status: '', etapa: '', atrasadas: parametroUrl('atrasadas') === '1' };
  let itens = [];
  let ordensPorNumero = new Map();

  const busca = campoBusca('Buscar peça (descrição, #código, OS ou cliente)...', (v) => { filtros.busca = v; desenhar(); }, { classe: 'flex-1' });
  const filtroStatus = selecao([], { vazio: 'Todos os status', 'aria-label': 'Status' });
  filtroStatus.addEventListener('change', () => { filtros.status = filtroStatus.value; desenhar(); });
  const filtroEtapa = selecao([], { vazio: 'Todas as etapas', 'aria-label': 'Etapa da rota' });
  filtroEtapa.addEventListener('change', () => { filtros.etapa = filtroEtapa.value; desenhar(); });
  [filtroStatus, filtroEtapa].forEach((s) => s.classList.add('largura-filtro'));
  const soAtrasadas = interruptor('Só atrasadas', { marcado: filtros.atrasadas, aoMudar: (v) => { filtros.atrasadas = v; desenhar(); } });

  const grade = el('div', { class: 'grade sm-2 lg-3 xl-4' }, esqueleto(1, 'esqueleto-bloco'));
  const resumo = el('p', { class: 'texto-xs texto-mudo' });
  const novaPeca = botao('Nova peça', { variante: 'primario', icone: 'plus' });
  novaPeca.addEventListener('click', () => {
    const abertas = [...ordensPorNumero.values()].filter((o) => !osEncerrada(o)).sort((a, b) => b.numero - a.numero);
    if (!abertas.length) { mostrarErro({ mensagens: ['Não há OS aberta. Abra uma OS em "Nova OS" (ela já nasce com os itens).'] }); return; }
    dialogoNovoItem({ ordensAbertas: abertas, aoSalvar: carregar }).catch(mostrarErro);
  });

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Peças', 'Itens das OS: fotos, rota de produção e histórico.', [novaPeca]),
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' }, busca, filtroStatus, filtroEtapa, soAtrasadas)),
    grade, resumo));

  // Baixa a primeira foto quando o card entra na tela.
  const observador = 'IntersectionObserver' in window ? new IntersectionObserver((entradas) => {
    entradas.forEach((en) => {
      if (!en.isIntersecting) return;
      observador.unobserve(en.target);
      en.target.carregarFoto?.();
    });
  }, { rootMargin: '200px' }) : null;

  function card(i) {
    const os = ordensPorNumero.get(i.osNumero);
    const foto = el('div', { class: 'peca-foto' }, icone('boxes'));
    const canto = el('div', { class: 'canto' }, i.atrasado ? status('Atrasada') : null, os ? prioridade(os.prioridade) : null);
    foto.append(canto);
    if (i.quantidadeFotos > 0) {
      foto.carregarFoto = async () => {
        try {
          const fotos = await get(`/itens/${i.codigo}/fotos`);
          if (!fotos.length) return;
          const url = await urlDaFoto(fotos[0].codigo);
          const img = el('img', { src: url, alt: i.descricao, loading: 'lazy', width: 512, height: 512 });
          foto.querySelector('.icone')?.replaceWith(img);
        } catch {
          /* fica o ícone */
        }
      };
      if (observador) observador.observe(foto); else foto.carregarFoto();
    }
    const etapas = (i.etapas || []).filter((e) => !e.retrabalho).map((e) => e.rotaNome).join(' → ') || 'Sem rota';
    return el('a', { class: 'card clicavel' + (i.atrasado ? ' destaque-perigo' : ''), href: `peca.html?codigo=${i.codigo}` },
      foto,
      el('div', { class: 'card-p4 pilha-2' },
        el('div', { class: 'linha-entre' }, el('span', { class: 'mono texto-sm negrito texto-primario', text: nItem(i.codigo) }), status(i.status)),
        el('p', { class: 'texto-sm medio', text: i.descricao }),
        el('p', { class: 'texto-xs texto-mudo truncar', text: `${os?.clienteNome ?? ''}${i.prazoEntrega ? ` · prazo ${data(i.prazoEntrega)}` : ''}` }),
        el('div', { class: 'linha-entre divisor-topo texto-xs' },
          el('span', { class: 'etiqueta-os', text: nOs(i.osNumero) }),
          el('span', { class: 'texto-mudo truncar' }, `${etapas} · `, el('span', { class: 'seminegrito', text: quantidade(i.qtdTotal, i.unidade) })))));
  }

  function filtrar() {
    const termo = normalizar(filtros.busca);
    return itens.filter((i) => {
      if (filtros.status && i.status !== filtros.status) return false;
      if (filtros.etapa && !(i.etapas || []).some((e) => e.rotaNome === filtros.etapa)) return false;
      if (filtros.atrasadas && !i.atrasado) return false;
      if (termo) {
        const os = ordensPorNumero.get(i.osNumero);
        const alvo = normalizar(`${i.descricao} #${i.codigo} os-${i.osNumero} os ${i.osNumero} ${os?.clienteNome ?? ''}`);
        if (!alvo.includes(termo)) return false;
      }
      return true;
    });
  }

  function desenhar() {
    const lista = filtrar();
    resumo.textContent = `${lista.length} de ${itens.length} peças`;
    trocar(grade, lista.length ? lista.map(card) : el('div', { class: 'card sm-span-6' }, vazio(itens.length ? 'Nenhuma peça com esses filtros.' : 'Nenhuma peça cadastrada.', 'boxes')));
  }

  async function carregar() {
    try {
      ref.esquecer('ordens');
      const [lista, ordens, statusItens, etapas] = await Promise.all([get('/itens'), ref.ordens(), ref.statusItens(), ref.etapas()]);
      itens = lista;
      ordensPorNumero = new Map(ordens.map((o) => [o.numero, o]));
      preencherSelecao(filtroStatus, statusItens.map((s) => s.nome), { vazio: 'Todos os status', valor: filtros.status });
      preencherSelecao(filtroEtapa, etapas.map((e) => e.nome), { vazio: 'Todas as etapas', valor: filtros.etapa });
      desenhar();
    } catch (e) {
      trocar(grade, vazio('Não foi possível carregar as peças.', 'circle-alert'));
      mostrarErro(e);
    }
  }

  carregar();
}
