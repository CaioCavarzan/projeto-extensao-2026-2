// Detalhe da OS (INCLUÍDO em 21/10/2026), no layout do ordens.$id.tsx do protótipo.
// Ações conforme o fluxo de status (StatusOsEnum): Criada -> Em produção (automático ao iniciar a 1ª etapa,
// RN-OS-06) -> Pausada / Concluída (todos os itens executados ou cancelados, RN-OS-04); Cancelar é só do ADMIN e
// cancela os itens não executados (RN-OS-05). OS concluída não reabre (RN-OS-03).
// ALTERADO (18/11/2026) [E3-33]: integração com a Etapa 3 do banco:
//  - nova aba "Auditoria", com a linha do tempo da função dbo.fn_auditoria_timeline_os (GET /api/ordens/{n}/auditoria);
//  - o cancelamento agora é feito pela procedure dbo.sp_cancelar_ordem_servico, que também tira as etapas pendentes
//    dos cronogramas futuros; a resposta traz avisos (itens cancelados, cronogramas afetados ou vazios), mostrados
//    depois da mensagem de sucesso.

import { get, post, put } from '../api.js';
import { dialogoNovoItem, etapaAtual, osAtrasada, osEncerrada, progressoDaOs } from '../componentes/os.js';
import { data, dataHora, lerPrioridade, moeda, nItem, nOs, quantidade } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
// ALTERADO (18/11/2026) [E3-33]: + caixaInfo (explicação da aba Auditoria).
import {
  abas, badge, botao, cabecalho, caixaInfo, campo, cartao, confirmar, dialogo, el, entrada, esqueleto, icone, info,
  mostrarErro, ocupado, parametroUrl, prioridade, status, sucesso, tabela, trocar, vazio, voltar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'ordens', titulo: 'Ordem de Serviço' });

if (pagina) {
  const { conteudo, admin } = pagina;
  const numero = Number(parametroUrl('numero'));
  const area = el('div', { class: 'pilha' }, voltar('Voltar', 'ordens.html'), esqueleto(5, 'esqueleto-bloco'));
  conteudo.append(area);

  async function carregar() {
    try {
      if (!Number.isInteger(numero) || numero <= 0) throw { status: 404, mensagens: ['OS não encontrada.'] };
      const [os, historico] = await Promise.all([get(`/ordens/${numero}`), get(`/ordens/${numero}/historico`)]);
      document.title = `${nOs(os.numero)} — Fênix`;
      desenhar(os, historico);
    } catch (e) {
      trocar(area, voltar('Voltar', 'ordens.html'), vazio(e.status === 404 ? `OS ${numero || ''} não encontrada.` : 'Não foi possível carregar a OS.', 'clipboard-list'));
      if (e.status !== 404) mostrarErro(e);
    }
  }

  async function acaoStatus(botaoEl, caminho, mensagem, pergunta) {
    if (pergunta && !(await confirmar(pergunta.texto, { titulo: pergunta.titulo, textoConfirmar: pergunta.botao, perigo: pergunta.perigo }))) return;
    await ocupado(botaoEl, async () => {
      try {
        // ALTERADO (18/11/2026) [E3-33]: era "await post(...); sucesso(mensagem)" sem ler a resposta. A resposta do
        // cancelamento traz os avisos da procedure (itens cancelados, etapas tiradas de cronogramas, cronograma vazio),
        // mostrados como lista dentro do aviso de sucesso.
        const resposta = await post(`/ordens/${numero}/${caminho}`);
        // um aviso só, com a lista (a pilha de avisos mostra no máximo 3 ao mesmo tempo)
        if (resposta?.avisos?.length) sucesso(mensagem, { lista: resposta.avisos, duracao: 9000 });
        else sucesso(mensagem);
        ref.esquecer('ordens');
        await carregar();
      } catch (e) {
        mostrarErro(e);
      }
    });
  }

  function botoesStatus(os) {
    const lista = [];
    const b = (texto, nomeIcone, caminho, msg, pergunta, variante = 'contorno') => {
      const el2 = botao(texto, { icone: nomeIcone, variante });
      el2.addEventListener('click', () => acaoStatus(el2, caminho, msg, pergunta));
      return el2;
    };
    if (os.status === 'Em produção') {
      lista.push(b('Pausar', 'pause', 'pausar', `${nOs(numero)} pausada.`));
      lista.push(b('Concluir', 'circle-check', 'concluir', `${nOs(numero)} concluída.`, {
        titulo: 'Concluir a OS', texto: 'A OS só é concluída quando todos os itens estão executados ou cancelados, e depois não reabre (RN-OS-03).', botao: 'Concluir',
      }));
    }
    if (os.status === 'Pausada') lista.push(b('Retomar', 'play', 'retomar', `${nOs(numero)} retomada.`));
    if (!osEncerrada(os) && admin) {
      lista.push(b('Cancelar OS', 'ban', 'cancelar', `${nOs(numero)} cancelada.`, {
        // ALTERADO (18/11/2026) [E3-33]: o texto passou a avisar que as etapas pendentes saem dos cronogramas futuros
        // (o que a procedure sp_cancelar_ordem_servico faz).
        titulo: 'Cancelar a OS', texto: 'Os itens ainda não executados também serão cancelados e as etapas pendentes deles saem dos cronogramas de hoje em diante; os executados ficam como estão (RN-OS-05). Esta ação não pode ser desfeita.', botao: 'Cancelar OS', perigo: true,
      }, 'perigo-contorno'));
    }
    return lista;
  }

  function dialogoPrioridade(os) {
    ref.escalaPrioridade().then((escala) => {
      const s = entrada({ type: 'number', min: String(escala.minimo), max: String(escala.maximo), step: '1', inputmode: 'numeric', value: os.prioridade ?? '', placeholder: `${escala.minimo} a ${escala.maximo}` });
      const salvar = botao('Salvar', { variante: 'primario' });
      const dlg = dialogo({
        titulo: `Prioridade da ${nOs(numero)}`,
        conteudo: campo('Prioridade', s, { dica: `Número inteiro de ${escala.minimo} a ${escala.maximo} (maior número = mais prioritária). Vazio = sem prioridade.` }),
        botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
      });
      salvar.addEventListener('click', () => ocupado(salvar, async () => {
        try {
          const valor = lerPrioridade(s.value, escala.minimo, escala.maximo);
          if (valor === undefined) {
            dlg.mostrarErro({ mensagens: [`Prioridade: informe um número inteiro de ${escala.minimo} a ${escala.maximo}.`] });
            return;
          }
          await put(`/ordens/${numero}/prioridade`, { prioridade: valor });
          dlg.fechar();
          sucesso('Prioridade alterada.');
          await carregar();
        } catch (e) {
          dlg.mostrarErro(e);
        }
      }));
    }).catch(mostrarErro);
  }

  function desenhar(os, historico) {
    const encerrada = osEncerrada(os);
    const prog = progressoDaOs(os);
    const acoes = [];
    if (!encerrada) {
      acoes.push(botao('Prioridade', { icone: 'pencil', onclick: () => dialogoPrioridade(os) }));
      acoes.push(...botoesStatus(os));
      acoes.push(botao('Adicionar item', { variante: 'primario', icone: 'plus', onclick: () => dialogoNovoItem({ numeroFixo: numero, aoSalvar: carregar }).catch(mostrarErro) }));
    }

    const valor = el('div', {}, el('span', { text: moeda(os.valorOs) }));
    if (os.valorIncompleto) valor.append(' ', badge('valor incompleto', 'alerta'));
    const cardInfo = cartao({
      titulo: 'Informações gerais', tituloSecao: true, classe: 'lg-span-3',
      corpo: el('div', { class: 'grade sm-2 lg-4 grade-6' },
        info('building-2', 'Cliente', os.clienteNome),
        info('user', 'Registrada por', os.registradaPor || '—'),
        info('calendar', 'Abertura', dataHora(os.dataEntrada)),
        // ALTERADO (18/11/2026) [E3-33]: a nota explica que a soma é feita pelo banco (triggers da Etapa 3).
        info('dollar-sign', 'Valor total', valor, os.valorIncompleto ? 'Há itens sem valor (RN-OS-15)' : 'Soma dos itens não cancelados, calculada pelo banco'),
        el('div', { class: 'linha sm-span-2 lg-span-4' }, status(os.status), osAtrasada(os) && !encerrada ? status('Atrasada') : null, prioridade(os.prioridade))),
    });
    const barra = el('div', { class: 'barra-preenchida' });
    barra.style.width = `${prog.percentual}%`;
    const cardProgresso = cartao({
      titulo: 'Progresso', classe: 'progresso brilho',
      corpo: [el('div', { class: 'numero-grande', text: `${prog.percentual}%` }),
        el('p', { class: 'texto-xs texto-mudo mt-1', text: `${prog.executados} de ${prog.total} itens executados` }),
        el('div', { class: 'barra mt-3' }, barra)],
    });

    const tabItens = () => el('div', { class: 'card card-tabela' }, tabela({
      colunas: [{ titulo: 'Fotos' }, { titulo: 'Cód.' }, { titulo: 'Descrição' }, { titulo: 'Qtd' }, { titulo: 'Etapa atual' },
        { titulo: 'Status' }, { titulo: 'Prazo' }, { titulo: 'Valor', classe: 'direita' }, { titulo: 'Ação', classe: 'direita' }],
      linhas: os.itens,
      textoVazio: 'Nenhum item nesta OS.',
      aoClicar: (i) => { location.href = `peca.html?codigo=${i.codigo}`; },
      classeLinha: (i) => (i.atrasado ? 'linha-atrasada' : ''),
      montar: (i) => [
        el('span', { class: 'linha texto-mudo', title: `${i.quantidadeFotos} foto(s)` }, icone('camera'), String(i.quantidadeFotos)),
        el('span', { class: 'mono seminegrito texto-primario', text: nItem(i.codigo) }),
        i.descricao,
        quantidade(i.qtdTotal, i.unidade),
        el('span', { class: 'texto-mudo', text: etapaAtual(i) }),
        el('span', { class: 'linha' }, status(i.status), i.atrasado ? status('Atrasada') : null),
        el('span', { class: i.atrasado ? 'texto-perigo seminegrito' : 'texto-mudo', text: data(i.prazoEntrega) }),
        moeda(i.valorTotalItem),
        { conteudo: el('div', { class: 'acoes' }, el('a', { class: 'btn btn-fantasma btn-sm', href: `peca.html?codigo=${i.codigo}`, text: 'Ver' })) },
      ],
    }));

    const tabHistorico = () => el('div', { class: 'card card-p6' }, historico.length ? el('div', { class: 'linha-tempo' },
      historico.map((h, i) => el('div', { class: 'linha-tempo-item' },
        el('div', { class: 'linha-tempo-marca' }, el('div', { class: 'linha-tempo-ponto' }), i < historico.length - 1 ? el('div', { class: 'linha-tempo-fio' }) : null),
        el('div', { class: 'linha-tempo-texto' }, el('p', { text: `Status: ${h.status}` }), el('p', { class: 'texto-xs texto-mudo', text: dataHora(h.dataHora) })))))
      : vazio('Sem histórico.', 'history'));

    const tabFinanceiro = () => {
      const validos = os.itens.filter((i) => i.status !== 'Cancelada');
      return el('div', { class: 'pilha-4' },
        el('div', { class: 'card card-tabela' }, tabela({
          colunas: [{ titulo: 'Item' }, { titulo: 'Status' }, { titulo: 'Entra na soma?' }, { titulo: 'Valor', classe: 'direita' }],
          linhas: os.itens,
          montar: (i) => [
            `${nItem(i.codigo)} · ${i.descricao}`,
            status(i.status),
            i.status === 'Cancelada' ? el('span', { class: 'texto-mudo', text: 'Não (cancelado)' }) : (i.valorTotalItem == null ? el('span', { class: 'texto-alerta', text: 'Não (sem valor)' }) : 'Sim'),
            moeda(i.valorTotalItem),
          ],
        })),
        el('div', { class: 'card card-p5 linha-entre' },
          el('div', {}, el('p', { class: 'texto-xs texto-mudo maiusculas', text: 'Valor da OS' }),
            el('p', { class: 'texto-xs texto-mudo', text: `${validos.length} item(ns) não cancelado(s); itens sem valor ficam fora da soma (RN-OS-15).` })),
          el('span', { class: 'texto-lg negrito texto-primario', text: moeda(os.valorOs) })));
    };

    // INCLUÍDO (18/11/2026) [E3-33]: aba Auditoria. A linha do tempo junta, em ordem, os status da OS, os status de
    // cada peça e o início/conclusão de cada etapa, lidos da função dbo.fn_auditoria_timeline_os quando a aba abre.
    // O nome ao lado do evento é o que a função devolve: nas etapas, o operador; nos status, quem abriu a OS (o banco
    // não registra quem mudou o status), por isso o texto diz qual dos dois é.
    const CATEGORIA = { 'Ordem de Serviço': 'primario', 'Peça (Item)': 'info', 'Etapa de Produção': 'sucesso' };
    const tabAuditoria = () => {
      const area = el('div', { class: 'card card-p6' }, esqueleto(5));
      get(`/ordens/${numero}/auditoria`).then((eventos) => {
        trocar(area, eventos.length ? el('div', { class: 'linha-tempo' },
          eventos.map((ev, i) => el('div', { class: 'linha-tempo-item' },
            el('div', { class: 'linha-tempo-marca' }, el('div', { class: 'linha-tempo-ponto' }), i < eventos.length - 1 ? el('div', { class: 'linha-tempo-fio' }) : null),
            el('div', { class: 'linha-tempo-texto' },
              el('p', { class: 'linha' }, badge(ev.categoria, CATEGORIA[ev.categoria] || 'contorno'), el('span', { class: 'medio', text: ev.evento })),
              el('p', { class: 'texto-xs texto-mudo', text: `${ev.referencia} · ${dataHora(ev.dataHora)} · ${ev.papelResponsavel === 'operador da etapa' ? 'operador' : 'OS aberta por'}: ${ev.responsavel || '—'}` })))))
          : vazio('Sem eventos.', 'history'));
      }).catch((e) => {
        trocar(area, vazio('Não foi possível carregar a auditoria.', 'circle-alert'));
        mostrarErro(e);
      });
      return el('div', { class: 'pilha-4' },
        caixaInfo('Rastreabilidade da OS (função dbo.fn_auditoria_timeline_os): status da OS, status das peças e início/conclusão das etapas, com o tempo de cada etapa. Nos status, o nome é de quem abriu a OS: o banco guarda a data da mudança, mas não quem mudou.'),
        area);
    };

    const controle = abas([
      { id: 'itens', rotulo: `Peças (${os.itens.length})`, conteudo: tabItens },
      { id: 'historico', rotulo: 'Histórico', conteudo: tabHistorico },
      { id: 'financeiro', rotulo: 'Financeiro', conteudo: tabFinanceiro },
      { id: 'auditoria', rotulo: 'Auditoria', conteudo: tabAuditoria }, // INCLUÍDO (18/11/2026) [E3-33]
    ]);

    const avisos = [];
    if (encerrada) avisos.push(el('div', { class: 'alerta alerta-info' }, icone('info'), el('div', { text: os.status === 'Concluída' ? 'OS concluída: não aceita alterações e não reabre (RN-OS-03). Ainda é possível registrar a entrega e anexar fotos nos itens.' : 'OS cancelada: não aceita alterações.' })));

    trocar(area,
      voltar('Voltar', 'ordens.html'),
      cabecalho(nOs(os.numero), `${os.clienteNome} · aberta em ${data(os.dataEntrada)}`, acoes),
      avisos,
      el('div', { class: 'grade lg-4' }, cardInfo, cardProgresso),
      controle.elemento);
  }

  carregar();
}
