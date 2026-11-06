// Clientes (INCLUÍDO em 21/10/2026) — o "modelo de CRUD" da Etapa 2, no layout do clientes.tsx do protótipo.
//
// Especialização (regras do grupo, garantidas no back-end pelo ClienteService):
//  - cliente é PF (CPF) ou PJ (CNPJ), nunca os dois: o tipo é escolhido no cadastro e não muda depois;
//  - o cliente PF pode ser a mesma pessoa de um funcionário (mesmo CPF) e o cliente PJ a mesma de um fornecedor
//    (mesmo CNPJ): o back-end liga o novo papel à pessoa existente e avisa;
//  - funcionário nunca é cliente PJ, e fornecedor nunca é cliente PF.
// Permissões (RN-SIS-03): ADMIN e OPERADOR cadastram e alteram; só o ADMIN exclui e corrige CPF/CNPJ.
// RN-CLI-03: antes de gravar, avisa se já existe cliente com nome parecido (não bloqueia).
// RN-PF-06: o nome social, quando existe, aparece no lugar do nome (nomeExibicao).

import { del, get, post, put } from '../api.js';
import { camposPessoa, selosPapeis, TEXTO_HERANCA } from '../componentes/pessoa.js';
import { cnpjLimpo, documento, formatarCnpj, formatarCpf, moeda, normalizar, soDigitos, textoOuNulo } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  areaTexto, avisoAcesso, botao, cabecalho, caixaInfo, campo, campoBusca, confirmar, dialogo, el, entrada, esqueleto,
  icone, mostrarAvisos, mostrarErro, ocupado, parametroUrl, pedirTexto, prioridade, selecao, sucesso, tabela, trocar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'clientes', titulo: 'Clientes' });

if (pagina) {
  const { conteudo, admin } = pagina;
  const filtros = { busca: '', tipo: '', fonte: '' };
  let clientes = [];
  let fontes = [];
  let resumoOs = new Map(); // codigo do cliente -> { qtd, total }

  const filtroTipo = selecao([{ valor: 'PF', rotulo: 'Pessoa física' }, { valor: 'PJ', rotulo: 'Pessoa jurídica' }], { vazio: 'PF e PJ', 'aria-label': 'Tipo' });
  filtroTipo.addEventListener('change', () => { filtros.tipo = filtroTipo.value; desenhar(); });
  const filtroFonte = selecao([], { vazio: 'Todas as fontes', 'aria-label': 'Fonte de aquisição' });
  filtroFonte.addEventListener('change', () => { filtros.fonte = filtroFonte.value; desenhar(); });
  [filtroTipo, filtroFonte].forEach((s) => s.classList.add('largura-filtro'));
  const areaTabela = el('div', {}, esqueleto(6));
  const resumo = el('p', { class: 'texto-xs texto-mudo' });

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Clientes', 'Cadastro de clientes e histórico de OS. Clique em um cliente para visualizar ou editar.', [
      botao('Novo cliente', { variante: 'primario', icone: 'plus', onclick: () => dialogoNovo().catch(mostrarErro) }),
    ]),
    avisoAcesso(admin),
    el('div', { class: 'card card-p4' }, el('div', { class: 'linha' },
      campoBusca('Buscar cliente (nome, CPF/CNPJ ou e-mail)...', (v) => { filtros.busca = v; desenhar(); }, { classe: 'flex-1' }), filtroTipo, filtroFonte)),
    el('div', { class: 'card card-tabela' }, areaTabela),
    resumo));

  function filtrar() {
    const termo = normalizar(filtros.busca);
    const termoDoc = cnpjLimpo(filtros.busca);
    return clientes.filter((c) => {
      if (filtros.tipo && c.tipo !== filtros.tipo) return false;
      if (filtros.fonte && String(c.fonteCodigo) !== filtros.fonte) return false;
      if (!termo) return true;
      return normalizar(`${c.nomeExibicao} ${c.nome} ${c.nomeFantasia || ''} ${c.email || ''}`).includes(termo)
        || (termoDoc.length >= 3 && (c.cpf || c.cnpj || '').includes(termoDoc));
    });
  }

  function desenhar() {
    const lista = filtrar();
    resumo.textContent = `${lista.length} de ${clientes.length} clientes`;
    trocar(areaTabela, tabela({
      colunas: [{ titulo: 'Cliente' }, { titulo: 'Tipo' }, { titulo: 'CPF / CNPJ' }, { titulo: 'Fonte' }, { titulo: 'Prioridade' },
        { titulo: 'OS', classe: 'direita' }, { titulo: 'Total', classe: 'direita' }, { titulo: '' }],
      linhas: lista,
      textoVazio: clientes.length ? 'Nenhum cliente com esses filtros.' : 'Nenhum cliente cadastrado.',
      aoClicar: (c) => dialogoVer(c),
      montar: (c) => {
        const os = resumoOs.get(c.codigo) || { qtd: 0, total: 0 };
        const nomeCivil = c.nomeExibicao !== c.nome ? el('span', { class: 'texto-xs texto-mudo', text: `Nome civil: ${c.nome}` }) : null;
        return [
          el('div', { class: 'linha-3' },
            el('span', { class: 'avatar avatar-9 avatar-mudo' }, icone(c.tipo === 'PJ' ? 'building-2' : 'user')),
            el('div', { class: 'coluna' }, el('span', { class: 'medio', text: c.nomeExibicao }), nomeCivil, selosPapeis(c.papeis, ['Cliente PF', 'Cliente PJ']))),
          el('span', { class: 'etiqueta mono', text: c.tipo }),
          el('span', { class: 'mono texto-xs texto-mudo sem-quebra', text: documento(c.cpf, c.cnpj) }),
          c.fonte ? el('span', { class: 'etiqueta', text: c.fonte }) : el('span', { class: 'texto-mudo', text: '—' }),
          prioridade(c.prioridade),
          el('span', { class: 'seminegrito', text: String(os.qtd) }),
          el('span', { class: 'seminegrito texto-primario sem-quebra', text: moeda(os.total) }),
          { conteudo: el('div', { class: 'acoes' }, botao('', { variante: 'fantasma', tamanho: 'icone-sm', icone: 'eye', titulo: 'Ver cliente', onclick: () => dialogoVer(c) })) },
        ];
      },
    }));
  }

  // ---------------------------------------------------------------- formulário (novo e edição)
  function montarFormulario(c = null) {
    const novo = !c;
    let tipo = c?.tipo || 'PJ';
    const pessoa = camposPessoa({ valor: c ? { nome: c.nome, email: c.email, telefone: c.telefone, endereco: c.endereco } : {} });
    const doc = entrada({ inputmode: 'text', autocomplete: 'off', value: c ? documento(c.cpf, c.cnpj) : '' });
    if (!novo) doc.readOnly = true;
    const nomeSocial = entrada({ maxlength: 200, value: c?.nomeSocial || '' });
    const nomeFantasia = entrada({ maxlength: 100, value: c?.nomeFantasia || '', placeholder: 'Nome comercial' });
    const ie = entrada({ maxlength: 14, value: c?.ie || '', placeholder: 'Só números ou ISENTO' });
    const fonte = selecao(fontes.map((f) => ({ valor: f.codigo, rotulo: f.nome })), { vazio: 'Como conheceu?', valor: c?.fonteCodigo ?? '' });
    // RN-CLI-09: a prioridade é calculada pelo sistema (maior faturamento = maior prioridade); aqui só aparece.
    const prio = entrada({ readOnly: true, value: novo ? 'Calculada pelo sistema' : String(c?.prioridade ?? 0) });
    const obs = areaTexto({ rows: 4, maxlength: 300, value: c?.observacao || '', placeholder: 'Anote informações relevantes sobre este cliente...' });

    const camposTipo = el('div', { class: 'form-grade c6' });
    const radios = el('div', { class: 'opcoes-caixa' });
    const desenharTipo = () => {
      const pf = tipo === 'PF';
      pessoa.entradaNome.placeholder = pf ? 'Nome do cliente' : 'Razão social da empresa';
      pessoa.nome.querySelector('label').firstChild.textContent = pf ? 'Nome completo' : 'Razão social';
      doc.placeholder = pf ? '000.000.000-00' : '00.000.000/0000-00';
      const rotuloDoc = pf ? 'CPF' : 'CNPJ';
      const campoDoc = campo(rotuloDoc, doc, { largura: 'c2', obrigatorio: novo });
      if (!novo && admin) {
        campoDoc.append(botao(`Corrigir ${rotuloDoc}`, { tamanho: 'sm', variante: 'fantasma', icone: 'pencil', onclick: () => corrigirDocumento(c) }));
      }
      trocar(camposTipo, pessoa.nome, campoDoc,
        pf ? campo('Nome social', nomeSocial, { largura: 'c3', dica: 'Se houver, aparece no lugar do nome (RN-PF-06).' }) : campo('Nome fantasia', nomeFantasia, { largura: 'c3' }),
        pf ? campo('Fonte de aquisição', fonte, { largura: 'c3' }) : campo('Inscrição estadual', ie, { largura: 'c3' }),
        pessoa.email, pessoa.telefone,
        pf ? null : campo('Fonte de aquisição', fonte, { largura: 'c6' }));
    };
    if (novo) {
      ['PF', 'PJ'].forEach((t) => {
        const r = el('input', { type: 'radio', name: 'tipo-cliente', value: t, checked: t === tipo });
        r.addEventListener('change', () => {
          tipo = t;
          doc.value = '';
          desenharTipo();
        });
        radios.append(el('label', { class: 'opcao' }, r, el('span', { text: t === 'PF' ? 'Pessoa Física' : 'Pessoa Jurídica' })));
      });
      // máscara conforme o tipo escolhido (CPF numérico; CNPJ alfanumérico, RN-PJ-03)
      doc.addEventListener('input', () => {
        const fim = doc.selectionStart === doc.value.length;
        doc.value = tipo === 'PF' ? formatarCpf(doc.value) : formatarCnpj(doc.value);
        if (fim) doc.setSelectionRange(doc.value.length, doc.value.length);
      });
    }
    desenharTipo();

    const elemento = el('div', { class: 'pilha' },
      el('section', { class: 'secao-form' },
        el('h3', { class: 'secao-titulo', text: 'Dados principais' }),
        novo ? el('div', { class: 'campo' }, el('span', { class: 'rotulo', text: 'Tipo de pessoa' }), radios, el('p', { class: 'dica', text: 'Cliente é PF ou PJ, nunca os dois; o tipo não muda depois do cadastro.' }))
          : el('p', { class: 'texto-sm' }, el('span', { class: 'texto-mudo', text: 'Tipo: ' }), tipo === 'PF' ? 'Pessoa Física' : 'Pessoa Jurídica'),
        camposTipo),
      el('section', { class: 'secao-form' }, el('h3', { class: 'secao-titulo', text: 'Endereço' }), pessoa.endereco),
      el('section', { class: 'secao-form' }, el('h3', { class: 'secao-titulo', text: 'Informações adicionais' }),
        el('div', { class: 'form-grade' }, campo('Prioridade', prio, { largura: 'c2', dica: 'De 0 a 100, pelo faturamento do cliente (RN-CLI-09).' }), campo('Observações', obs, { largura: 'c6' }))),
      novo ? caixaInfo(TEXTO_HERANCA) : null);

    return {
      elemento,
      tipo: () => tipo,
      valor() {
        const comum = { pessoa: pessoa.valor(), fonteCodigo: Number(fonte.value) || null, observacao: textoOuNulo(obs.value) };
        if (!novo) return { ...comum, nomeSocial: textoOuNulo(nomeSocial.value), nomeFantasia: textoOuNulo(nomeFantasia.value), ie: textoOuNulo(ie.value) };
        return tipo === 'PF'
          ? { ...comum, cpf: soDigitos(doc.value), nomeSocial: textoOuNulo(nomeSocial.value) }
          : { ...comum, cnpj: cnpjLimpo(doc.value), nomeFantasia: textoOuNulo(nomeFantasia.value), ie: textoOuNulo(ie.value) };
      },
    };
  }

  /** RN-CLI-03: devolve true se pode gravar (sem nome parecido ou o usuário confirmou). */
  async function confirmarSemelhantes(nome, ignorarCodigo) {
    if (!nome || nome.length < 3) return true;
    const parecidos = (await get('/clientes/semelhantes?nome=' + encodeURIComponent(nome))).filter((c) => c.codigo !== ignorarCodigo);
    if (!parecidos.length) return true;
    return confirmar('Já existe cliente com nome parecido. Deseja continuar?', {
      titulo: 'Possível cliente duplicado', textoConfirmar: 'Continuar',
      detalhe: parecidos.slice(0, 5).map((c) => `• ${c.nomeExibicao} (${c.tipo} ${documento(c.cpf, c.cnpj)})`).join('\n') + (parecidos.length > 5 ? `\n... e mais ${parecidos.length - 5}` : ''),
    });
  }

  async function dialogoNovo() {
    // o botão pode ser clicado antes de as listas carregarem
    if (!fontes.length) fontes = await ref.fontes();
    const form = montarFormulario();
    const salvar = botao('Cadastrar', { variante: 'primario', icone: 'save' });
    const dlg = dialogo({ titulo: 'Cadastrar novo cliente', conteudo: form.elemento, largura: '3xl', botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar] });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      dlg.limparErro();
      const dados = form.valor();
      try {
        if (!(await confirmarSemelhantes(dados.pessoa.nome))) return;
        const c = await post(form.tipo() === 'PF' ? '/clientes/pf' : '/clientes/pj', dados);
        dlg.fechar();
        sucesso(`Cliente ${c.nomeExibicao} cadastrado.`);
        mostrarAvisos(c.avisos);
        ref.esquecer('clientes');
        if (parametroUrl('novo') === '1') { location.href = `ordem-nova.html?cliente=${c.codigo}`; return; }
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  function dialogoVer(c) {
    const form = montarFormulario(c);
    const os = resumoOs.get(c.codigo) || { qtd: 0, total: 0 };
    const salvar = botao('Salvar alterações', { variante: 'primario', icone: 'save' });
    const esquerda = el('div', { class: 'esquerda' },
      el('a', { class: 'btn btn-contorno', href: `ordem-nova.html?cliente=${c.codigo}` }, icone('clipboard-list'), el('span', { text: 'Abrir OS' })));
    if (admin) {
      const excluir = botao('Excluir', { variante: 'perigo-contorno', icone: 'trash-2' });
      excluir.addEventListener('click', async () => {
        if (!(await confirmar(`Excluir o cliente ${c.nomeExibicao}? Cliente com OS não é excluído (RN-CLI-02). Se a pessoa não tiver outro papel, ela e o endereço também são apagados.`, { titulo: 'Excluir cliente', textoConfirmar: 'Excluir', perigo: true }))) return;
        await ocupado(excluir, async () => {
          try {
            await del(`/clientes/${c.codigo}`);
            dlg.fechar();
            sucesso('Cliente excluído.');
            ref.esquecer('clientes');
            await carregar();
          } catch (e) {
            dlg.mostrarErro(e);
          }
        });
      });
      esquerda.append(excluir);
    }
    const dlg = dialogo({
      titulo: 'Editar cliente', largura: '3xl',
      descricao: `${c.papeis?.length > 1 ? `Papéis desta pessoa: ${c.papeis.join(' · ')}. ` : ''}OS realizadas: ${os.qtd} · total ${moeda(os.total)}.`,
      conteudo: form.elemento,
      botoes: [esquerda, botao('Fechar', { onclick: () => dlg.fechar() }), salvar],
    });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      dlg.limparErro();
      try {
        const r = await put(`/clientes/${c.codigo}`, form.valor());
        dlg.fechar();
        sucesso('Cliente atualizado.');
        mostrarAvisos(r.avisos);
        ref.esquecer('clientes');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  /** Só ADMIN (RN-SIS-03): corrige o CPF ou o CNPJ da pessoa, em todos os papéis que usam o documento. */
  async function corrigirDocumento(c) {
    const pf = c.tipo === 'PF';
    const novo = await pedirTexto({
      titulo: `Corrigir ${pf ? 'CPF' : 'CNPJ'}`, rotulo: `Novo ${pf ? 'CPF' : 'CNPJ'}`, valor: documento(c.cpf, c.cnpj),
      dica: 'A correção vale para todos os papéis da pessoa (cliente, funcionário ou fornecedor) que usam este documento.',
      mascara: (e) => { e.addEventListener('input', () => { e.value = pf ? formatarCpf(e.value) : formatarCnpj(e.value); }); },
    });
    if (novo === null) return;
    try {
      const r = await put(`/pessoas/${c.codigo}/${pf ? 'cpf' : 'cnpj'}`, { documento: pf ? soDigitos(novo) : cnpjLimpo(novo) });
      sucesso(`${pf ? 'CPF' : 'CNPJ'} corrigido.`);
      mostrarAvisos(r?.mensagens);
      document.querySelectorAll('dialog').forEach((d) => d.close());
      ref.esquecer('clientes');
      await carregar();
    } catch (e) {
      mostrarErro(e);
    }
  }

  async function carregar() {
    try {
      const [lista, ordens, listaFontes] = await Promise.all([get('/clientes'), get('/ordens'), ref.fontes()]);
      clientes = lista.sort((a, b) => a.nomeExibicao.localeCompare(b.nomeExibicao));
      fontes = listaFontes;
      resumoOs = new Map();
      ordens.forEach((o) => {
        const r = resumoOs.get(o.clienteCodigo) || { qtd: 0, total: 0 };
        r.qtd++;
        if (o.status !== 'Cancelada') r.total += Number(o.valorOs || 0);
        resumoOs.set(o.clienteCodigo, r);
      });
      const atual = filtroFonte.value;
      trocar(filtroFonte, el('option', { value: '', text: 'Todas as fontes' }), fontes.map((f) => el('option', { value: String(f.codigo), text: f.nome })));
      filtroFonte.value = atual;
      desenhar();
    } catch (e) {
      trocar(areaTabela, el('div', { class: 'vazio' }, icone('circle-alert'), el('p', { text: 'Não foi possível carregar os clientes.' })));
      mostrarErro(e);
    }
  }

  await carregar();
  if (parametroUrl('novo') === '1') dialogoNovo().catch(mostrarErro);
}
