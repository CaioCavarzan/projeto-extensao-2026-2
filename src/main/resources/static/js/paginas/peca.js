// Detalhe da peça (item da OS) — INCLUÍDO em 21/10/2026, no layout do pecas.$id.tsx do protótipo, mais a rota de
// produção (ItensRotasProcessos), as fotos (até 10, RN-ITE-30) e o histórico de status.
//
// Rota (regras do ItemRotaProcessoService): iniciar exige operador e a etapa anterior concluída; concluir exige a
// etapa iniciada; a hora vem do servidor; etapa concluída não reabre — vira retrabalho (nova etapa igual no fim da
// rota); só etapa não iniciada sai da rota; corrigir datas é só do ADMIN.

import { del, get, post, put, urlDaFoto } from '../api.js';
import { formularioItem } from '../componentes/item.js';
import { itemEncerrado, osEncerrada, referenciasDoItem } from '../componentes/os.js';
import { data, dataHora, moeda, nItem, nOs, numero, paraDataHoraLocal, quantidade } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  botao, botaoIcone, cabecalho, campo, cartao, confirmar, dialogo, el, entrada, esqueleto, icone, info,
  mostrarAvisos, mostrarErro, ocupado, parametroUrl, pedirTexto, prioridade, selecao, status, sucesso, trocar, vazio, voltar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'pecas', titulo: 'Peça' });

if (pagina) {
  const { conteudo, admin, usuario } = pagina;
  const codigo = Number(parametroUrl('codigo'));
  const area = el('div', { class: 'pilha' }, voltar('Voltar para Peças', 'pecas.html'), esqueleto(5, 'esqueleto-bloco'));
  conteudo.append(area);
  const urlsFotos = [];

  async function carregar() {
    try {
      if (!Number.isInteger(codigo) || codigo <= 0) throw { status: 404 };
      const item = await get(`/itens/${codigo}`);
      const [os, historico, fotos, parametros] = await Promise.all([
        get(`/ordens/${item.osNumero}`), get(`/itens/${codigo}/historico`), get(`/itens/${codigo}/fotos`), ref.parametros(),
      ]);
      document.title = `Item ${nItem(codigo)} — Fênix`;
      desenhar(item, os, historico, fotos, parametros);
    } catch (e) {
      trocar(area, voltar('Voltar para Peças', 'pecas.html'), vazio(e.status === 404 ? `Peça ${nItem(codigo || '')} não encontrada.` : 'Não foi possível carregar a peça.', 'boxes'));
      if (e.status !== 404) mostrarErro(e);
    }
  }

  /** Executa a ação da API com o botão ocupado, mostra a mensagem e recarrega a tela. */
  function acao(botaoEl, executar, mensagem) {
    return ocupado(botaoEl, async () => {
      try {
        const r = await executar();
        if (mensagem) sucesso(mensagem);
        mostrarAvisos(r?.avisos);
        ref.esquecer('ordens', 'tintas');
        await carregar();
      } catch (e) {
        mostrarErro(e);
      }
    });
  }

  // ------------------------------------------------------------------ diálogos
  async function dialogoEditar(item) {
    const referencias = await referenciasDoItem();
    const form = formularioItem({ referencias, valor: item, modo: 'edicao' });
    const salvar = botao('Salvar alterações', { variante: 'primario', icone: 'save' });
    const dlg = dialogo({ titulo: `Editar item ${nItem(item.codigo)}`, conteudo: form.elemento, largura: '3xl', botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar] });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      const erros = form.erros();
      if (erros.length) { dlg.mostrarErro({ mensagens: erros }); return; }
      try {
        const r = await put(`/itens/${item.codigo}`, form.valor());
        dlg.fechar();
        sucesso('Item atualizado.');
        mostrarAvisos(r.avisos);
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function dialogoTinta(item) {
    const tintas = (await ref.tintas()).filter((t) => t.corCodigo === item.corCodigo);
    const s = selecao(tintas.map((t) => ({ valor: t.codigo, rotulo: `${t.descricao} · ${t.fornecedorNome} · ${Number(t.estoqueAtual) > 0 ? `${numero(t.estoqueAtual)} ${t.unidadeEstoque}` : 'sem estoque'}` })),
      { vazio: tintas.length ? 'Sem tinta definida' : 'Nenhuma tinta cadastrada nesta cor', valor: item.tintaCodigo ?? '' });
    const aviso = el('p', { class: 'dica texto-alerta', hidden: true, text: 'Atenção: tinta sem estoque.' });
    s.addEventListener('change', () => {
      const t = tintas.find((x) => String(x.codigo) === s.value);
      aviso.hidden = !(t && !(Number(t.estoqueAtual) > 0)); // RN-ITE-12
    });
    const salvar = botao('Salvar', { variante: 'primario' });
    const dlg = dialogo({
      titulo: 'Definir tinta', descricao: `Cor pedida: ${item.corNome}. Só aparecem tintas dessa cor (RN-ITE-04).`,
      conteudo: [campo('Tinta', s), aviso], botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      try {
        const r = await put(`/itens/${item.codigo}/tinta`, { tintaCodigo: Number(s.value) || null });
        dlg.fechar();
        sucesso('Tinta definida.');
        mostrarAvisos(r.avisos);
        ref.esquecer('tintas');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function dialogoIniciar(etapa) {
    const funcionarios = await ref.funcionarios();
    const s = selecao(funcionarios.map((f) => ({ valor: f.codigo, rotulo: `${f.nome} · ${f.cargo}` })), { vazio: 'Selecione o operador', valor: usuario.funcionarioCodigo });
    const iniciar = botao('Iniciar etapa', { variante: 'primario', icone: 'play' });
    const dlg = dialogo({
      titulo: `Iniciar ${etapa.rotaNome}`, descricao: 'A hora de início é a do servidor, no momento do clique.',
      conteudo: campo('Operador', s, { obrigatorio: true }), botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), iniciar],
    });
    iniciar.addEventListener('click', () => ocupado(iniciar, async () => {
      try {
        await post(`/etapas-item/${etapa.codigo}/iniciar`, { funcionarioCodigo: Number(s.value) || null });
        dlg.fechar();
        sucesso(`${etapa.rotaNome} iniciada.`);
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function dialogoCorrigirDatas(etapa) {
    const funcionarios = await ref.funcionarios();
    const inicio = entrada({ type: 'datetime-local', value: paraDataHoraLocal(etapa.dataInicio) });
    const fim = entrada({ type: 'datetime-local', value: paraDataHoraLocal(etapa.dataConclusao) });
    const operador = selecao(funcionarios.map((f) => ({ valor: f.codigo, rotulo: f.nome })), { valor: etapa.funcionarioCodigo ?? '' });
    const salvar = botao('Salvar correção', { variante: 'primario' });
    const dlg = dialogo({
      titulo: `Corrigir datas — ${etapa.rotaNome}`, descricao: 'Correção manual, só do Administrador. Fica registrada no log de auditoria.',
      conteudo: el('div', { class: 'form-grade' }, campo('Início', inicio, { largura: 'c3' }), campo('Conclusão', fim, { largura: 'c3', dica: etapa.dataConclusao ? undefined : 'Etapa ainda não concluída.' }), campo('Operador', operador, { largura: 'c6' })),
      botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
    });
    if (!etapa.dataConclusao) fim.disabled = true;
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      try {
        await put(`/etapas-item/${etapa.codigo}/datas`, {
          dataInicio: inicio.value || null, dataConclusao: etapa.dataConclusao ? (fim.value || null) : null, funcionarioCodigo: Number(operador.value) || null,
        });
        dlg.fechar();
        sucesso('Datas corrigidas.');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function dialogoAdicionarEtapa(item) {
    const etapas = await ref.etapas();
    const s = selecao(etapas.map((e) => ({ valor: e.codigo, rotulo: e.nome })), { vazio: 'Selecione a etapa' });
    const salvar = botao('Adicionar à rota', { variante: 'primario', icone: 'plus' });
    const dlg = dialogo({
      titulo: 'Adicionar etapa', descricao: 'A etapa entra no fim da rota do item.',
      conteudo: campo('Etapa', s, { obrigatorio: true }), botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      try {
        await post(`/itens/${item.codigo}/etapas`, { rotaCodigo: Number(s.value) || null });
        dlg.fechar();
        sucesso('Etapa adicionada à rota.');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  function ampliarFoto(url, descricao) {
    dialogo({ titulo: descricao, largura: '4xl', conteudo: el('img', { src: url, alt: descricao, class: 'foto-ampliada' }) });
  }

  let enviandoFotos = false;

  async function enviarFotos(arquivos, item, fotosAtuais, maximo, botaoEl) {
    const lista = [...arquivos];
    if (!lista.length) return;
    if (enviandoFotos) {
      mostrarErro({ mensagens: ['Aguarde o envio das fotos anteriores terminar.'] });
      return;
    }
    if (fotosAtuais + lista.length > maximo) {
      mostrarErro({ mensagens: [`Este item já tem ${fotosAtuais} foto(s) e o limite é ${maximo}. Exclua uma para anexar outra.`] });
      return;
    }
    enviandoFotos = true;
    try {
      await enviarFotosSequencia(lista, item, botaoEl);
    } finally {
      enviandoFotos = false;
    }
  }

  async function enviarFotosSequencia(lista, item, botaoEl) {
    await ocupado(botaoEl, async () => {
      let enviadas = 0;
      for (const arq of lista) {
        const form = new FormData();
        form.append('arquivo', arq);
        try {
          await post(`/itens/${item.codigo}/fotos`, form);
          enviadas++;
        } catch (e) {
          mostrarErro(e, `Foto "${arq.name}" não enviada:`);
        }
      }
      if (enviadas) sucesso(`${enviadas} foto(s) anexada(s).`);
      await carregar();
    });
  }

  // ------------------------------------------------------------------ tela
  function desenhar(item, os, historico, fotos, parametros) {
    urlsFotos.splice(0).forEach((u) => URL.revokeObjectURL(u));
    const encerrado = itemEncerrado(item);
    const osFechada = osEncerrada(os);
    const podeAlterar = !encerrado && !osFechada; // RN-ITE-07 e RN-OS-07
    const temPintura = (item.etapas || []).some((e) => e.rotaNome?.toLowerCase() === 'pintura');

    const acoes = [el('a', { class: 'btn btn-contorno', href: `ordem.html?numero=${item.osNumero}` }, icone('clipboard-list'), el('span', { text: `Ver ${nOs(item.osNumero)}` }))];
    if (podeAlterar) acoes.push(botao('Editar', { icone: 'pencil', onclick: () => dialogoEditar(item).catch(mostrarErro) }));
    if (!osFechada && item.status === 'Aguardando execução') {
      const b = botao('Pausar', { icone: 'pause' });
      b.addEventListener('click', () => acao(b, () => post(`/itens/${codigo}/pausar`), 'Item pausado.'));
      acoes.push(b);
    }
    if (!osFechada && item.status === 'Pausada') {
      const b = botao('Retomar', { icone: 'play' });
      b.addEventListener('click', () => acao(b, () => post(`/itens/${codigo}/retomar`), 'Item retomado.'));
      acoes.push(b);
    }
    if (podeAlterar) {
      const b = botao('Cancelar item', { icone: 'ban', variante: 'perigo-contorno' });
      b.addEventListener('click', async () => {
        if (!(await confirmar('O item cancelado sai da soma da OS e não executa mais etapas. Esta ação não pode ser desfeita.', { titulo: `Cancelar o item ${nItem(codigo)}`, textoConfirmar: 'Cancelar item', perigo: true }))) return;
        acao(b, () => post(`/itens/${codigo}/cancelar`), 'Item cancelado.');
      });
      acoes.push(b);
    }
    if (item.status === 'Executada') {
      const b = botao(item.nrOse ? 'Alterar entrega' : 'Registrar entrega', { variante: 'primario', icone: 'truck' });
      b.addEventListener('click', async () => {
        const nr = await pedirTexto({
          titulo: 'Registrar entrega', rotulo: 'Nº da OSE assinada pelo cliente', valor: item.nrOse || '',
          dica: 'Ordem de Serviço Externa, assinada na entrega (RN-ITE-28). Até 50 caracteres.', textoConfirmar: 'Registrar',
        });
        if (nr === null) return;
        acao(b, () => put(`/itens/${codigo}/entrega`, { nrOse: nr }), 'Entrega registrada.');
      });
      acoes.push(b);
    }

    // Foto principal + selos
    const fotoPrincipal = el('div', { class: 'foto-grande' }, icone('image'));
    const cardFoto = el('div', { class: 'card' }, fotoPrincipal,
      el('div', { class: 'card-p4 linha-entre' }, el('span', { class: 'linha' }, status(item.status), item.atrasado ? status('Atrasada') : null), prioridade(os.prioridade)));

    const tinta = el('div', { class: 'linha' }, el('span', { text: item.tintaDescricao || '—' }));
    if (temPintura && podeAlterar && item.corCodigo) tinta.append(botao(item.tintaCodigo ? 'Trocar' : 'Definir tinta', { tamanho: 'sm', variante: 'fantasma', icone: 'palette', onclick: () => dialogoTinta(item).catch(mostrarErro) }));
    const cardDetalhes = cartao({
      titulo: 'Detalhes da peça', tituloSecao: true, classe: 'lg-span-2',
      corpo: el('div', { class: 'grade sm-2 grade-6' },
        info('clipboard-list', 'OS vinculada', el('a', { class: 'mono seminegrito texto-primario', href: `ordem.html?numero=${item.osNumero}`, text: nOs(item.osNumero) })),
        info('building-2', 'Cliente', os.clienteNome),
        info('package', 'Quantidade', el('span', { class: 'seminegrito', text: quantidade(item.qtdTotal, item.unidade) })),
        info('droplet', 'Cor pedida', item.corNome || (temPintura ? 'Não informada' : 'Sem pintura')),
        info('palette', 'Tinta', tinta),
        info('calendar', 'Data de entrada', dataHora(item.dataEntrada)),
        info('calendar-clock', 'Prazo de entrega', el('span', { class: item.atrasado ? 'texto-perigo seminegrito' : '', text: data(item.prazoEntrega) }), item.atrasado ? 'Atrasado (RN-ITE-22)' : undefined),
        info('dollar-sign', 'Valor', el('span', { class: 'seminegrito texto-primario', text: moeda(item.valorTotalItem) }), item.valorTotalItem == null ? 'Sem valor: OS com valor incompleto' : undefined),
        info('gauge', 'Score de prioridade', item.scorePrioridade != null ? numero(item.scorePrioridade) : 'Calculado pelo sistema'),
        info('check-check', 'Finalização', item.dataFinalizacao ? dataHora(item.dataFinalizacao) : '—'),
        info('truck', 'Entrega (OSE)', item.nrOse || 'Não entregue')),
    });

    // Rota de produção
    const linhasEtapas = (item.etapas || []).map((e, idx, todas) => {
      const iniciada = !!e.dataInicio;
      const concluida = !!e.dataConclusao;
      const anterioresOk = todas.slice(0, idx).every((x) => x.dataConclusao);
      const situacao = concluida
        ? `Concluída em ${dataHora(e.dataConclusao)} · operador: ${e.funcionarioNome || '—'}`
        : iniciada ? `Em execução desde ${dataHora(e.dataInicio)} · operador: ${e.funcionarioNome || '—'}` : (anterioresOk ? 'Pronta para iniciar' : 'Aguardando a etapa anterior');
      const botoesEtapa = [];
      if (!osFechada && item.status !== 'Cancelada') {
        if (!iniciada && anterioresOk) botoesEtapa.push(botao('Iniciar', { tamanho: 'sm', variante: 'primario', icone: 'play', onclick: () => dialogoIniciar(e).catch(mostrarErro) }));
        if (iniciada && !concluida) {
          const b = botao('Concluir', { tamanho: 'sm', variante: 'primario', icone: 'check' });
          b.addEventListener('click', () => acao(b, () => post(`/etapas-item/${e.codigo}/concluir`), `${e.rotaNome} concluída.`));
          botoesEtapa.push(b);
        }
        if (concluida) {
          const b = botao('Retrabalho', { tamanho: 'sm', icone: 'rotate-ccw' });
          b.addEventListener('click', async () => {
            if (!(await confirmar(`Registrar retrabalho de ${e.rotaNome}? Uma nova etapa igual entra no fim da rota; a etapa concluída não é reaberta.`, { titulo: 'Registrar retrabalho', textoConfirmar: 'Registrar' }))) return;
            acao(b, () => post(`/etapas-item/${e.codigo}/retrabalho`), 'Retrabalho registrado.');
          });
          botoesEtapa.push(b);
        }
        if (!iniciada && !encerrado) {
          botoesEtapa.push(botaoIcone('trash-2', 'Tirar da rota', async (ev) => {
            const b = ev.currentTarget; // guardado antes do await: depois do evento o currentTarget vira null
            if (!(await confirmar(`Tirar ${e.rotaNome} da rota do item?`, { titulo: 'Remover etapa', textoConfirmar: 'Remover', perigo: true }))) return;
            acao(b, () => del(`/etapas-item/${e.codigo}`), 'Etapa removida da rota.');
          }));
        }
      }
      if (admin && iniciada) botoesEtapa.push(botaoIcone('calendar-clock', 'Corrigir datas (ADMIN)', () => dialogoCorrigirDatas(e).catch(mostrarErro)));
      return el('div', { class: 'etapa-item' },
        el('div', { class: 'etapa-num' + (concluida ? ' concluida' : iniciada ? ' executando' : ''), text: String(e.ordem) }),
        el('div', { class: 'flex-1' },
          el('div', { class: 'linha' }, el('span', { class: 'seminegrito', text: e.rotaNome }), e.retrabalho ? status('Retrabalho') : null),
          el('p', { class: 'texto-xs texto-mudo', text: situacao })),
        el('div', { class: 'etapa-acoes' }, botoesEtapa));
    });
    const cardRota = cartao({
      titulo: 'Rota de produção', tituloSecao: true,
      descricao: 'Etapas na ordem; cada uma começa só depois que a anterior foi concluída.',
      acoes: podeAlterar ? botao('Adicionar etapa', { tamanho: 'sm', icone: 'plus', onclick: () => dialogoAdicionarEtapa(item).catch(mostrarErro) }) : null,
      corpo: linhasEtapas.length ? el('div', { class: 'pilha-2' }, linhasEtapas) : vazio('Sem etapas: inclua pelo menos uma (RN-ITE-03).', 'route'),
    });

    // Fotos
    const maximo = parametros.maxFotosPorItem;
    const seletor = el('input', { type: 'file', accept: 'image/jpeg,image/png', multiple: true });
    const enviar = botao('Adicionar fotos', { tamanho: 'sm', icone: 'upload', onclick: () => seletor.click(), disabled: fotos.length >= maximo });
    seletor.addEventListener('change', () => { enviarFotos(seletor.files, item, fotos.length, maximo, enviar); seletor.value = ''; });
    const grade = el('div', { class: 'fotos-grade' });
    fotos.forEach((f, i) => {
      const tile = el('div', { class: 'foto-tile' }, el('div', { class: 'esqueleto largura-total', 'aria-hidden': 'true' }));
      const remover = el('button', { type: 'button', class: 'remover', title: 'Excluir foto', 'aria-label': 'Excluir foto' }, icone('x', 'icone-3'));
      remover.addEventListener('click', async () => {
        if (!(await confirmar('A foto e o arquivo serão apagados.', { titulo: 'Excluir foto', textoConfirmar: 'Excluir', perigo: true }))) return;
        try {
          await del(`/fotos/${f.codigo}`);
          sucesso('Foto excluída.');
          await carregar();
        } catch (e) {
          mostrarErro(e);
        }
      });
      urlDaFoto(f.codigo).then((url) => {
        urlsFotos.push(url);
        const img = el('img', { src: url, alt: `Foto ${i + 1} de ${item.descricao}`, loading: 'lazy' });
        img.addEventListener('click', () => ampliarFoto(url, `Foto ${i + 1} — ${item.descricao}`));
        trocar(tile, img, remover);
        if (i === 0) trocar(fotoPrincipal, el('img', { src: url, alt: item.descricao }));
      }).catch(() => trocar(tile, el('div', { class: 'foto-vazia' }, icone('image')), remover));
      grade.append(tile);
    });
    for (let i = fotos.length; i < Math.min(fotos.length + 3, maximo); i++) {
      grade.append(el('button', { type: 'button', class: 'foto-vazia', title: 'Adicionar foto', 'aria-label': 'Adicionar foto', onclick: () => seletor.click() }, icone('image-plus')));
    }
    const cardFotos = cartao({
      titulo: `Fotos (${fotos.length} de ${maximo})`, tituloSecao: true,
      descricao: `JPG ou PNG de até ${parametros.tamanhoMaximoFotoMb} MB; a foto é reduzida para 1920 px ao salvar. Pode anexar mesmo com a OS encerrada.`,
      acoes: [enviar, seletor], corpo: grade,
    });

    // Histórico
    const cardHistorico = cartao({
      titulo: 'Histórico de status', tituloSecao: true,
      corpo: historico.length ? el('div', { class: 'linha-tempo' }, historico.map((h, i) => el('div', { class: 'linha-tempo-item' },
        el('div', { class: 'linha-tempo-marca' }, el('div', { class: 'linha-tempo-ponto' }), i < historico.length - 1 ? el('div', { class: 'linha-tempo-fio' }) : null),
        el('div', { class: 'linha-tempo-texto' }, el('p', { text: h.status }), el('p', { class: 'texto-xs texto-mudo', text: dataHora(h.dataHora) })))))
        : vazio('Sem histórico.', 'history'),
    });

    const avisos = [];
    if (item.status === 'Pendente') avisos.push(el('div', { class: 'alerta alerta-aviso' }, icone('triangle-alert'), el('div', { text: 'Item pendente: confira a rota, a cor pedida (se tem Pintura) e os dados obrigatórios. Ele passa a "Aguardando execução" sozinho quando estiver completo.' })));
    if (osFechada) avisos.push(el('div', { class: 'alerta alerta-info' }, icone('info'), el('div', { text: `A ${nOs(os.numero)} está ${os.status.toLowerCase()}: o item não é mais alterado, mas ainda aceita fotos e o registro da entrega (RN-OS-07).` })));

    trocar(area,
      voltar('Voltar para Peças', 'pecas.html'),
      cabecalho(`Item ${nItem(item.codigo)}`, item.descricao, acoes),
      avisos,
      el('div', { class: 'grade lg-3 grade-6' }, cardFoto, cardDetalhes),
      el('div', { class: 'grade lg-2 grade-6' }, cardRota, cardHistorico),
      cardFotos);
  }

  carregar();
}
