// Funções comuns das telas de OS e de peças (INCLUÍDO em 21/10/2026).

import { post } from '../api.js';
import * as ref from '../referencias.js';
import { botao, dialogo, el, mostrarAvisos, ocupado, selecao, campo, sucesso } from '../ui.js';
import { formularioItem } from './item.js';
import { nOs } from '../formato.js';

/** OS encerrada (Concluída ou Cancelada) não aceita alterações (RN-OS-07). */
export const osEncerrada = (os) => os.status === 'Concluída' || os.status === 'Cancelada';
/** Item encerrado (Executada ou Cancelada) não é alterado (RN-ITE-07). */
export const itemEncerrado = (item) => item.status === 'Executada' || item.status === 'Cancelada';

/** Prazo mais próximo entre os itens ainda abertos da OS. */
export function prazoDaOs(os) {
  const prazos = (os.itens || []).filter((i) => !itemEncerrado(i) && i.prazoEntrega).map((i) => i.prazoEntrega).sort();
  return prazos[0] || null;
}

/** A OS tem algum item atrasado (RN-ITE-22)? */
export const osAtrasada = (os) => (os.itens || []).some((i) => i.atrasado);

/** Progresso: itens executados entre os não cancelados. */
export function progressoDaOs(os) {
  const validos = (os.itens || []).filter((i) => i.status !== 'Cancelada');
  const executados = validos.filter((i) => i.status === 'Executada').length;
  return { executados, total: validos.length, percentual: validos.length ? Math.round((executados / validos.length) * 100) : 0 };
}

/** Etapa atual do item: a primeira da rota ainda não concluída. */
export function etapaAtual(item) {
  const pendente = (item.etapas || []).find((e) => !e.dataConclusao);
  if (!pendente) return item.etapas?.length ? 'Rota concluída' : 'Sem etapas';
  return `${pendente.ordem}. ${pendente.rotaNome}${pendente.dataInicio ? ' (em execução)' : ''}`;
}

/** Carrega as listas que o formulário do item usa. */
export async function referenciasDoItem() {
  const [etapas, cores, tintas, parametros] = await Promise.all([ref.etapas(), ref.cores(), ref.tintas(), ref.parametros()]);
  return { etapas, cores, tintas, parametros };
}

/**
 * Diálogo "Adicionar item" numa OS aberta (POST /api/ordens/{numero}/itens).
 * ordensAbertas: quando informado, o usuário escolhe a OS (tela Peças); senão usa numeroFixo.
 */
export async function dialogoNovoItem({ numeroFixo, ordensAbertas, aoSalvar }) {
  const referencias = await referenciasDoItem();
  const form = formularioItem({ referencias, modo: 'novo' });
  let escolhaOs = null;
  const conteudo = [];
  if (!numeroFixo) {
    escolhaOs = selecao(ordensAbertas.map((o) => ({ valor: o.numero, rotulo: `${nOs(o.numero)} · ${o.clienteNome} · ${o.status}` })), { vazio: 'Selecione a OS' });
    conteudo.push(el('div', { class: 'mb-4' }, campo('OS vinculada', escolhaOs, { obrigatorio: true, dica: 'Só OS que não estão concluídas nem canceladas (RN-OS-07).' })));
  }
  conteudo.push(form.elemento);
  const salvar = botao(numeroFixo ? 'Adicionar item' : 'Cadastrar', { variante: 'primario', icone: 'package-plus' });
  const dlg = dialogo({
    titulo: numeroFixo ? `Adicionar item à ${nOs(numeroFixo)}` : 'Cadastrar nova peça',
    descricao: 'O item nasce "Pendente" e passa a "Aguardando execução" quando a rota e os dados obrigatórios estão completos.',
    conteudo, largura: '3xl',
    botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar],
  });
  form.focar();
  salvar.addEventListener('click', () => ocupado(salvar, async () => {
    dlg.limparErro();
    const numero = numeroFixo || Number(escolhaOs.value);
    const erros = form.erros();
    if (!numero) erros.unshift('Selecione a OS.');
    if (erros.length) { dlg.mostrarErro({ mensagens: erros }); return; }
    try {
      const item = await post(`/ordens/${numero}/itens`, form.valor());
      dlg.fechar();
      sucesso(`Item #${item.codigo} incluído na ${nOs(numero)}.`);
      mostrarAvisos(item.avisos);
      ref.esquecer('ordens');
      aoSalvar?.(item);
    } catch (e) {
      dlg.mostrarErro(e);
    }
  }));
}
