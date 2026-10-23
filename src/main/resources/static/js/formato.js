// Formatação de valores para a tela e máscaras de digitação (INCLUÍDO em 21/10/2026).
//
// Regra geral do back-end (RN-PES-13, RN-END-07, RN-PF-03, RN-PJ-03): CPF, CNPJ, CEP e telefone são gravados
// sem máscara. A máscara existe só na tela: aqui ela é aplicada ao digitar e retirada antes de enviar.

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Só os dígitos do texto. */
export function soDigitos(texto) {
  return String(texto ?? '').replace(/\D/g, '');
}

/** CNPJ sem máscara, em maiúsculas (RN-PJ-03: aceita o formato alfanumérico da IN RFB 2.229/2024). */
export function cnpjLimpo(texto) {
  return String(texto ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, 14);
}

/** Texto aparado; vazio vira null (o back-end grava NULL nos opcionais vazios). */
export function textoOuNulo(texto) {
  const t = String(texto ?? '').trim();
  return t === '' ? null : t;
}

// ------------------------------------------------------------------ datas (sem passar por UTC: evita trocar o dia)

function partesData(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/.exec(String(iso ?? ''));
  return m ? { ano: m[1], mes: m[2], dia: m[3], hora: m[4], minuto: m[5] } : null;
}

export function data(iso) {
  const p = partesData(iso);
  return p ? `${p.dia}/${p.mes}/${p.ano}` : '—';
}

export function diaMes(iso) {
  const p = partesData(iso);
  return p ? `${p.dia}/${p.mes}` : '—';
}

export function dataHora(iso) {
  const p = partesData(iso);
  if (!p) return '—';
  return p.hora ? `${p.dia}/${p.mes}/${p.ano} ${p.hora}:${p.minuto}` : `${p.dia}/${p.mes}/${p.ano}`;
}

export function hora(iso) {
  const p = partesData(iso);
  return p?.hora ? `${p.hora}:${p.minuto}` : '';
}

/** Data de hoje no fuso do computador, no formato do <input type="date"> (AAAA-MM-DD). */
export function hojeISO(deslocamentoDias = 0) {
  const d = new Date();
  d.setDate(d.getDate() + deslocamentoDias);
  return isoDe(d);
}

export function isoDe(d) {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** "2026-10-21T08:30" para o <input type="datetime-local">. */
export function paraDataHoraLocal(iso) {
  const p = partesData(iso);
  return p && p.hora ? `${p.ano}-${p.mes}-${p.dia}T${p.hora}:${p.minuto}` : '';
}

export const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro',
  'Outubro', 'Novembro', 'Dezembro'];
export const MESES_CURTOS = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];

// ------------------------------------------------------------------ números e dinheiro

export function moeda(valor) {
  if (valor === null || valor === undefined || valor === '') return '—';
  const n = Number(valor);
  return Number.isFinite(n) ? MOEDA.format(n) : '—';
}

export function numero(valor, casas = 2) {
  if (valor === null || valor === undefined || valor === '') return '—';
  const n = Number(valor);
  if (!Number.isFinite(n)) return '—';
  return n.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas });
}

/**
 * % de retrabalho do PDCA (RN-IRP-09) a partir de IndicadorRetrabalhoResponse. Sem item executado no período não há
 * base para o cálculo: mostra "—" em vez de 0%, que daria a falsa impressão de produção sem retrabalho.
 */
export function percentualRetrabalho(ind, casas = 1) {
  return ind && Number(ind.itensExecutados) > 0 ? `${numero(ind.percentual ?? 0, casas)}%` : '—';
}

/** Quantidade com unidade: "12 PC", "3,5 KG". */
export function quantidade(valor, unidade) {
  const n = numero(valor);
  return unidade ? `${n} ${unidade}` : n;
}

/**
 * Converte o que foi digitado ("1.234,56", "12,5", "12.5") em número para o JSON (BigDecimal no back-end).
 * Devolve null se vazio e NaN se inválido (o formulário mostra a mensagem).
 */
export function lerDecimal(texto) {
  let t = String(texto ?? '').trim().replace(/\s|R\$/g, '');
  if (t === '') return null;
  if (t.includes(',')) t = t.replace(/\./g, '').replace(',', '.');
  if (!/^-?\d+(\.\d+)?$/.test(t)) return NaN;
  return Number(t);
}

/** Número para mostrar num campo de edição ("12,5"). */
export function decimalParaCampo(valor) {
  if (valor === null || valor === undefined || valor === '') return '';
  return String(valor).replace('.', ',');
}

// ------------------------------------------------------------------ documentos e contatos

export function formatarCpf(valor) {
  const d = soDigitos(valor).slice(0, 11);
  return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d)/, '.$1-$2');
}

export function formatarCnpj(valor) {
  const c = cnpjLimpo(valor);
  return c.replace(/^(\w{2})(\w)/, '$1.$2').replace(/^(\w{2})\.(\w{3})(\w)/, '$1.$2.$3')
    .replace(/\.(\w{3})(\w)/, '.$1/$2').replace(/(\w{4})(\w)/, '$1-$2');
}

export function formatarCep(valor) {
  const d = soDigitos(valor).slice(0, 8);
  return d.replace(/^(\d{5})(\d)/, '$1-$2');
}

export function formatarTelefone(valor) {
  const d = soDigitos(valor).slice(0, 11);
  if (d.length <= 2) return d ? `(${d}` : '';
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

export function documento(cpf, cnpj) {
  if (cpf) return formatarCpf(cpf);
  if (cnpj) return formatarCnpj(cnpj);
  return '—';
}

const FORMATADORES = { cpf: formatarCpf, cnpj: formatarCnpj, cep: formatarCep, telefone: formatarTelefone };

/** Liga a máscara a um campo de texto. */
export function mascarar(input, tipo) {
  const f = FORMATADORES[tipo];
  if (!f) return input;
  input.addEventListener('input', () => {
    const fim = input.selectionStart === input.value.length;
    input.value = f(input.value);
    if (fim) input.setSelectionRange(input.value.length, input.value.length);
  });
  if (input.value) input.value = f(input.value);
  return input;
}

/** Iniciais para o avatar ("Ana Souza" -> "AS"). */
export function iniciais(nome) {
  return String(nome ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase() || '?';
}

/** Texto sem acento e em minúsculas, para buscas e comparações. */
export function normalizar(texto) {
  return String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

/** Número da OS como aparece no protótipo ("OS-12"). */
export function nOs(numero) {
  return `OS-${numero}`;
}

/** Código do item ("#34"). */
export function nItem(codigo) {
  return `#${codigo}`;
}

/**
 * Prioridade digitada pelo usuário (RN-OS-16): número inteiro de minimo a maximo (0 a 100, GET /api/parametros).
 * Campo vazio = sem prioridade (null). Devolve undefined quando o texto não é um inteiro dentro da escala.
 */
export function lerPrioridade(texto, minimo, maximo) {
  const t = String(texto ?? '').trim();
  if (t === '') return null;
  const n = Number(t);
  return Number.isInteger(n) && n >= minimo && n <= maximo ? n : undefined;
}
