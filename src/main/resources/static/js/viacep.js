// RN-END-03 (INCLUÍDO em 21/10/2026): busca logradouro, cidade e UF pelo CEP no ViaCEP, feita pelo front.
// O endereço continua editável depois; o back-end valida e grava o CEP só com os 8 dígitos (RN-END-07).
// O ViaCEP está liberado no connect-src da Content-Security-Policy (SecurityConfig.CABECALHO_CSP).

import { soDigitos } from './formato.js';

const MENSAGEM_ERRO = 'CEP não encontrado; preencha o endereço.';

export async function buscarCep(cep) {
  const digitos = soDigitos(cep);
  if (digitos.length !== 8) throw new Error('CEP inválido: informe os 8 dígitos.');
  const controle = new AbortController();
  const limite = setTimeout(() => controle.abort(), 6000);
  try {
    const r = await fetch(`https://viacep.com.br/ws/${digitos}/json/`, {
      signal: controle.signal, credentials: 'omit', referrerPolicy: 'no-referrer', cache: 'force-cache',
    });
    if (!r.ok) throw new Error(MENSAGEM_ERRO);
    const j = await r.json();
    if (j.erro) throw new Error(MENSAGEM_ERRO);
    return { uf: j.uf || '', cidade: j.localidade || '', logradouro: j.logradouro || '' };
  } catch (e) {
    throw new Error(e?.message === MENSAGEM_ERRO ? MENSAGEM_ERRO : `${MENSAGEM_ERRO} (sem resposta do ViaCEP)`);
  } finally {
    clearTimeout(limite);
  }
}
