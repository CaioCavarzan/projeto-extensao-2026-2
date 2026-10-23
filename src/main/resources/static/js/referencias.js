// Listas de apoio usadas em várias telas (INCLUÍDO em 21/10/2026), lidas uma vez por página.

import { get } from './api.js';

const cache = new Map();

function carregar(chave, caminho) {
  if (!cache.has(chave)) {
    cache.set(chave, get(caminho).catch((e) => {
      cache.delete(chave);
      throw e;
    }));
  }
  return cache.get(chave);
}

/** Escala de prioridade, unidades, cargos, prazos, limites de foto e senha (GET /api/parametros). */
export const parametros = () => carregar('parametros', '/parametros');
export const cores = () => carregar('cores', '/cores');
/** Etapas cadastradas (RotasProcessos): Jateamento, Banho, Pintura... */
export const etapas = () => carregar('etapas', '/etapas');
export const fontes = () => carregar('fontes', '/fontes');
export const tintas = () => carregar('tintas', '/tintas');
export const funcionarios = () => carregar('funcionarios', '/funcionarios');
export const clientes = () => carregar('clientes', '/clientes');
export const fornecedores = () => carregar('fornecedores', '/fornecedores');
export const ordens = () => carregar('ordens', '/ordens');
export const statusOs = () => carregar('statusOs', '/status/os');
export const statusItens = () => carregar('statusItens', '/status/itens');

/** Descarta a lista guardada (depois de cadastrar ou alterar). */
export function esquecer(...chaves) {
  chaves.forEach((c) => cache.delete(c));
}

/** Limites da prioridade (RN-CLI-09 e RN-OS-16): hoje de 0 a 100. */
export async function escalaPrioridade() {
  const p = await parametros();
  return { minimo: p.prioridadeMinima, maximo: p.prioridadeMaxima };
}
