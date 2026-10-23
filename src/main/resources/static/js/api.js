// Acesso à API REST do back-end (INCLUÍDO em 21/10/2026).
//
// Todas as rotas ficam em /api (ver controllers/package-info.java). Os caminhos são relativos à página
// ("api/clientes"), então o sistema funciona tanto na raiz (localhost:8080) quanto num contexto do Tomcat
// (localhost:8080/projetoextensao). Erros chegam no formato ErroResponse do GlobalExceptionHandler:
// { status, erro, mensagens: [...], caminho, dataHora } — as mensagens são as da planilha de regras.

import { credencial, irParaLogin, limparSessao } from './sessao.js';

export class ErroApi extends Error {
  constructor(status, mensagens, dados = null) {
    super(mensagens.join(' '));
    this.name = 'ErroApi';
    this.status = status;
    this.mensagens = mensagens;
    this.dados = dados;
  }
}

const MENSAGENS_PADRAO = {
  0: 'Sem conexão com o servidor. Confira se a aplicação está rodando e tente de novo.',
  400: 'Confira os dados informados.',
  401: 'Sessão expirada ou acesso não autorizado. Entre novamente.',
  403: 'Ação permitida só para administradores.',
  404: 'Registro não encontrado.',
  409: 'A operação conflita com dados já gravados.',
  413: 'Envie uma imagem JPG ou PNG de até 10 MB.',
  500: 'Não foi possível concluir a operação. Tente de novo; se continuar, avise o administrador.',
};

function mensagemPadrao(status) {
  return MENSAGENS_PADRAO[status] || (status >= 500 ? MENSAGENS_PADRAO[500] : MENSAGENS_PADRAO[400]);
}

/**
 * Faz a requisição e devolve o JSON da resposta (ou null em 204).
 * opcoes.publica: rota sem login (cadastre-se e lembrete de senha) — não envia a credencial.
 * opcoes.credencial: usa esta credencial em vez da guardada (tela de login, antes de gravar a sessão).
 * opcoes.login: 401 é erro do formulário de login, não sessão expirada.
 * opcoes.blob: devolve o corpo como Blob (fotos).
 */
export async function requisitar(metodo, caminho, corpo, opcoes = {}) {
  const cabecalhos = { Accept: opcoes.blob ? 'image/*,application/json' : 'application/json' };
  const cred = opcoes.credencial ?? credencial();
  if (!opcoes.publica && cred) cabecalhos.Authorization = 'Basic ' + cred;
  let body;
  if (corpo instanceof FormData) {
    body = corpo; // o navegador monta o Content-Type multipart com o boundary
  } else if (corpo !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
    body = JSON.stringify(corpo);
  }

  let resposta;
  try {
    resposta = await fetch('api' + caminho, {
      method: metodo, headers: cabecalhos, body, cache: 'no-store', credentials: 'omit',
    });
  } catch {
    throw new ErroApi(0, [mensagemPadrao(0)]);
  }

  if (resposta.status === 401 && !opcoes.publica && !opcoes.login) {
    // RN-SIS-01: credencial recusada (senha trocada em outro lugar, usuário bloqueado...) — volta ao login.
    limparSessao();
    irParaLogin('expirada');
    throw new ErroApi(401, [mensagemPadrao(401)]);
  }
  if (resposta.status === 204) return null;

  const tipo = resposta.headers.get('Content-Type') || '';
  if (resposta.ok && opcoes.blob) return resposta.blob();

  let dados = null;
  if (tipo.includes('application/json')) {
    try { dados = await resposta.json(); } catch { dados = null; }
  }
  if (!resposta.ok) {
    const mensagens = Array.isArray(dados?.mensagens) && dados.mensagens.length ? dados.mensagens : [mensagemPadrao(resposta.status)];
    throw new ErroApi(resposta.status, mensagens, dados);
  }
  return dados;
}

export const get = (caminho, opcoes) => requisitar('GET', caminho, undefined, opcoes);
export const post = (caminho, corpo, opcoes) => requisitar('POST', caminho, corpo, opcoes);
export const put = (caminho, corpo, opcoes) => requisitar('PUT', caminho, corpo, opcoes);
export const del = (caminho, opcoes) => requisitar('DELETE', caminho, undefined, opcoes);

/** Monta "?a=1&b=2" ignorando valores vazios. */
export function consulta(parametros) {
  const p = new URLSearchParams();
  Object.entries(parametros).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, v);
  });
  const texto = p.toString();
  return texto ? '?' + texto : '';
}

/**
 * Foto de um item: a imagem exige login como o resto da API, então é baixada com a credencial e mostrada a partir
 * da memória (URL "blob:"). Quem chama deve liberar a URL com URL.revokeObjectURL quando a imagem sair da tela.
 */
export async function urlDaFoto(fotoCodigo) {
  const blob = await requisitar('GET', `/fotos/${fotoCodigo}/arquivo`, undefined, { blob: true });
  return URL.createObjectURL(blob);
}
