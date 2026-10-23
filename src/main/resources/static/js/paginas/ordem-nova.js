// Nova OS (INCLUÍDO em 21/10/2026). No protótipo era um diálogo com "Criar OS" e "Incluir peças"; pela RN-OS-01 a
// OS só é gravada com pelo menos um item, na mesma transação (POST /api/ordens com os itens). Por isso a abertura
// virou uma tela com os dados da OS e os itens juntos. Número, data de entrada e usuário que registrou não são
// digitados: vêm do banco e do servidor (RN-OS-08, RN-OS-11, RN-OS-12).

import { post } from '../api.js';
import { formularioItem } from '../componentes/item.js';
import { referenciasDoItem } from '../componentes/os.js';
import { documento, lerPrioridade, moeda, nOs, normalizar } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  avisarNaProximaTela, botao, cabecalho, caixaErro, campo, cartao, el, entrada, icone, mostrarErro, ocupado,
  parametroUrl, preencherSelecao, selecao, trocar, voltar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'ordens', titulo: 'Nova OS' });

if (pagina) {
  const { conteudo } = pagina;
  const areaErro = el('div', { hidden: true });
  try {
    const [clientes, escala, referencias] = await Promise.all([ref.clientes(), ref.escalaPrioridade(), referenciasDoItem()]);
    const ordenados = [...clientes].sort((a, b) => a.nomeExibicao.localeCompare(b.nomeExibicao));

    // ------------------------------------------------------------ dados da OS
    const filtroCliente = entrada({ type: 'search', placeholder: 'Filtrar clientes pelo nome ou documento...', class: 'input fundo-mudo' });
    const cliente = selecao([], { vazio: 'Selecione o cliente' });
    const prio = entrada({ type: 'number', min: String(escala.minimo), max: String(escala.maximo), step: '1', inputmode: 'numeric', placeholder: `${escala.minimo} a ${escala.maximo}` });
    const dicaPrio = el('p', { class: 'dica', text: `Opcional, de ${escala.minimo} a ${escala.maximo}. Já vem a do cliente.` });
    const opcoesClientes = (termo) => {
      const t = normalizar(termo);
      const lista = ordenados.filter((c) => !t || normalizar(c.nomeExibicao).includes(t) || normalizar(c.nome).includes(t)
        || (c.cpf || c.cnpj || '').includes(t.replace(/\W/g, '').toUpperCase()));
      const atual = cliente.value;
      preencherSelecao(cliente, lista.map((c) => ({ valor: c.codigo, rotulo: `${c.nomeExibicao} · ${c.tipo} ${documento(c.cpf, c.cnpj)}` })), { vazio: lista.length ? 'Selecione o cliente' : 'Nenhum cliente encontrado' });
      if (lista.some((c) => String(c.codigo) === atual)) cliente.value = atual;
    };
    filtroCliente.addEventListener('input', () => opcoesClientes(filtroCliente.value));
    opcoesClientes('');
    // RN-OS-17: ao escolher o cliente, sugere a prioridade dele (pode ser mudada).
    cliente.addEventListener('change', () => {
      const c = ordenados.find((x) => String(x.codigo) === cliente.value);
      prio.value = c?.prioridade != null ? String(c.prioridade) : '';
      dicaPrio.textContent = c?.prioridade != null ? `Sugerida pela prioridade do cliente (${c.prioridade} de ${escala.maximo}); pode mudar.` : 'Sem prioridade sugerida.';
    });
    const preSelecionado = parametroUrl('cliente');
    if (preSelecionado && ordenados.some((c) => String(c.codigo) === preSelecionado)) {
      cliente.value = preSelecionado;
      cliente.dispatchEvent(new Event('change'));
    }

    const cardOs = cartao({
      titulo: 'Dados da OS',
      corpo: el('div', { class: 'form-grade' },
        campo('Nº OS', entrada({ value: 'Gerado ao salvar', readOnly: true }), { largura: 'c2' }),
        campo('Data de abertura', entrada({ value: 'Hoje, pela hora do servidor', readOnly: true }), { largura: 'c2' }),
        campo('Registrada por', entrada({ value: pagina.usuario.nome, readOnly: true }), { largura: 'c2' }),
        el('div', { class: 'campo c4' }, el('label', { class: 'rotulo', for: cliente.id || 'cliente-os' }, 'Cliente', el('span', { class: 'obrigatorio', text: '*' })), filtroCliente, cliente,
          el('p', { class: 'dica' }, 'Cliente novo? ', el('a', { class: 'texto-primario', href: 'clientes.html?novo=1', text: 'Cadastre em Clientes' }), ' e volte aqui.')),
        el('div', { class: 'campo c2' }, el('label', { class: 'rotulo', for: 'prioridade-os', text: 'Prioridade' }), prio, dicaPrio)),
    });
    cliente.id = 'cliente-os';
    prio.id = 'prioridade-os';

    // ------------------------------------------------------------ itens (pelo menos um, RN-OS-01)
    const itens = [];
    const listaItens = el('div', { class: 'pilha-4' });
    const total = el('span', { class: 'texto-lg negrito texto-primario' });
    const notaTotal = el('p', { class: 'dica' });
    const atualizarTotal = () => {
      const valores = itens.map((f) => f.valorNumerico());
      const soma = valores.filter((v) => Number.isFinite(v)).reduce((a, b) => a + b, 0);
      total.textContent = moeda(soma);
      const semValor = valores.filter((v) => v === null).length;
      notaTotal.textContent = semValor ? `${semValor} item(ns) sem valor: a OS ficará com "valor incompleto" (RN-OS-15).` : 'Soma dos valores informados nos itens.';
    };
    const renumerar = () => itens.forEach((f, i) => { f.elemento.querySelector('h4').textContent = `Item ${i + 1}`; });
    const adicionarItem = () => {
      const f = formularioItem({
        referencias, modo: 'novo', titulo: `Item ${itens.length + 1}`,
        aoRemover: () => {
          if (itens.length === 1) { mostrarErro({ mensagens: ['Inclua pelo menos um item na OS.'] }); return; }
          itens.splice(itens.indexOf(f), 1);
          f.elemento.remove();
          renumerar();
          atualizarTotal();
        },
      });
      f.elemento.addEventListener('input', atualizarTotal);
      itens.push(f);
      listaItens.append(f.elemento);
      atualizarTotal();
      return f;
    };
    const cardItens = cartao({
      titulo: 'Itens (peças)', descricao: 'Cada item tem a sua rota de etapas, prazo e valor.',
      acoes: botao('Adicionar item', { icone: 'plus', tamanho: 'sm', onclick: () => adicionarItem().focar() }),
      corpo: listaItens,
    });

    // ------------------------------------------------------------ gravação
    const abrir = botao('Abrir OS', { variante: 'primario', icone: 'clipboard-list' });
    abrir.addEventListener('click', () => ocupado(abrir, async () => {
      trocar(areaErro); areaErro.hidden = true;
      const erros = itens.flatMap((f, i) => f.erros().map((m) => `Item ${i + 1}: ${m}`));
      if (lerPrioridade(prio.value, escala.minimo, escala.maximo) === undefined) erros.unshift(`Prioridade: informe um número inteiro de ${escala.minimo} a ${escala.maximo}.`);
      if (erros.length) { trocar(areaErro, caixaErro(erros)); areaErro.hidden = false; window.scrollTo({ top: 0, behavior: 'smooth' }); return; }
      try {
        const os = await post('/ordens', {
          clienteCodigo: Number(cliente.value) || null,
          prioridade: lerPrioridade(prio.value, escala.minimo, escala.maximo),
          itens: itens.map((f) => f.valor()),
        });
        ref.esquecer('ordens');
        avisarNaProximaTela(`${nOs(os.numero)} aberta com ${os.itens.length} item(ns).`, { tipo: 'sucesso' });
        [...(os.avisos || []), ...os.itens.flatMap((i) => i.avisos || [])].forEach((a) => avisarNaProximaTela(a, { tipo: 'alerta', duracao: 7000 }));
        location.href = `ordem.html?numero=${os.numero}`;
      } catch (e) {
        trocar(areaErro, caixaErro(e.mensagens || [e.message]));
        areaErro.hidden = false;
        window.scrollTo({ top: 0, behavior: 'smooth' });
        mostrarErro(e);
      }
    }));

    conteudo.append(el('div', { class: 'pilha' },
      voltar('Voltar', 'ordens.html'),
      cabecalho('Nova Ordem de Serviço', 'A OS é gravada junto com os itens: inclua pelo menos um (RN-OS-01).'),
      areaErro, cardOs, cardItens,
      el('div', { class: 'card card-p5 linha-entre' },
        el('div', {}, el('p', { class: 'texto-xs texto-mudo maiusculas', text: 'Valor total da OS' }), total, notaTotal),
        el('div', { class: 'linha' }, el('a', { class: 'btn btn-contorno', href: 'ordens.html', text: 'Cancelar' }), abrir))));
    adicionarItem();
  } catch (e) {
    conteudo.append(el('div', { class: 'vazio' }, icone('circle-alert'), el('p', { text: 'Não foi possível carregar os dados para abrir a OS.' })));
    mostrarErro(e);
  }
}
