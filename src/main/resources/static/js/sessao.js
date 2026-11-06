// Sessão do usuário no navegador (INCLUÍDO em 21/10/2026).
//
// O back-end usa HTTP Basic sem sessão no servidor (SecurityConfig): toda requisição à API leva o cabeçalho
// "Authorization: Basic base64(login:senha)". Por isso a tela guarda essa credencial no sessionStorage, que o
// navegador apaga ao fechar a aba, junto com os dados do usuário logado (resposta de GET /api/auth/me).
// Sair do sistema = apagar as duas chaves. A senha nunca vai para o localStorage nem para a URL.

const CHAVE_CREDENCIAL = 'fenix.credencial';
const CHAVE_USUARIO = 'fenix.usuario';

function armazenamento() {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/** Base64 de "login:senha" em UTF-8 (o Spring Security decodifica em UTF-8; btoa sozinho falha com acentos). */
export function codificarCredencial(login, senha) {
  const bytes = new TextEncoder().encode(`${login}:${senha}`);
  let binario = '';
  bytes.forEach((b) => { binario += String.fromCharCode(b); });
  return btoa(binario);
}

export function credencial() {
  try {
    return armazenamento()?.getItem(CHAVE_CREDENCIAL) || null;
  } catch {
    return null;
  }
}

export function usuarioSalvo() {
  try {
    const texto = armazenamento()?.getItem(CHAVE_USUARIO);
    return texto ? JSON.parse(texto) : null;
  } catch {
    return null;
  }
}

/** Grava a credencial e o usuário. Devolve false se o navegador bloquear o armazenamento da aba. */
export function salvarSessao(cred, usuario) {
  try {
    const s = armazenamento();
    if (!s) return false;
    s.setItem(CHAVE_CREDENCIAL, cred);
    s.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
    return true;
  } catch {
    return false;
  }
}

export function salvarUsuario(usuario) {
  try {
    armazenamento()?.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
  } catch {
    /* sem armazenamento: a próxima tela busca de novo em /api/auth/me */
  }
}

export function limparSessao() {
  try {
    const s = armazenamento();
    s?.removeItem(CHAVE_CREDENCIAL);
    s?.removeItem(CHAVE_USUARIO);
  } catch {
    /* nada a apagar */
  }
}

/** Telas internas aceitas no parâmetro "voltar" do login (evita redirecionar para outro site). */
export function destinoSeguro(destino) {
  return typeof destino === 'string' && /^[a-z-]+\.html(\?[\w=&%.+-]*)?$/.test(destino) && !destino.startsWith('login.html')
    ? destino
    : 'index.html';
}

/** Vai para a tela de login, lembrando a tela atual para voltar depois de entrar. */
export function irParaLogin(motivo) {
  const atual = (location.pathname.split('/').pop() || 'index.html') + location.search;
  const params = new URLSearchParams();
  if (motivo) params.set('motivo', motivo);
  if (atual && !atual.startsWith('login.html')) params.set('voltar', atual);
  location.replace('login.html' + (params.toString() ? '?' + params : ''));
}
