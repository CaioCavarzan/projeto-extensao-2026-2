// Tela de login (INCLUÍDO em 21/10/2026): abas Entrar, Cadastre-se e Senha, como o login.tsx do protótipo.
//
// Entrar: GET /api/auth/me com a credencial digitada; se a API aceitar, a credencial fica na sessão da aba.
// Cadastre-se (RN-USU-02): ativação da conta de um funcionário já cadastrado pela Fênix — ele confirma o CPF e
//   escolhe login, senha, pergunta e resposta de segurança. A conta nasce com perfil OPERADOR (RN-USU-03).
// Senha / lembrete (RN-USU-05): login -> pergunta de segurança -> resposta + nova senha.
// O protótipo pedia "login ou e-mail" e nome/e-mail no cadastro; aqui vale o que o back-end implementa:
// entrar só pelo login, e o nome e o e-mail já estão no cadastro do funcionário.

import { get, post } from '../api.js';
import { PERGUNTAS_SEGURANCA, SENHA } from '../config.js';
import { mascarar, soDigitos } from '../formato.js';
import { codificarCredencial, credencial, destinoSeguro, salvarSessao } from '../sessao.js';
import { abas, botao, caixaErro, caixaInfo, campo, el, entrada, icone, informar, ocupado, selecao, sucesso, trocar } from '../ui.js';

const parametros = new URLSearchParams(location.search);
const destino = destinoSeguro(parametros.get('voltar'));
const raiz = document.getElementById('app');
document.title = 'Entrar — Fênix';

// Já logado (credencial ainda válida): vai direto para o sistema.
if (credencial()) {
  try {
    await get('/auth/me', { login: true });
    location.replace(destino);
  } catch {
    /* credencial vencida: fica no login */
  }
}

function tituloForm(nomeIcone, texto) {
  return el('h2', { class: 'card-titulo' }, nomeIcone ? icone(nomeIcone) : null, el('span', { text: texto }));
}

function areaErro() {
  const area = el('div', { hidden: true });
  area.mostrar = (e) => { trocar(area, caixaErro(e?.mensagens || [e?.message || String(e)])); area.hidden = false; };
  area.limpar = () => { trocar(area); area.hidden = true; };
  return area;
}

function validarSenhaNova(senha, confirmacao) {
  if (senha.length < SENHA.minimo || senha.length > SENHA.maximo) return `A senha precisa ter de ${SENHA.minimo} a ${SENHA.maximo} caracteres.`;
  if (senha !== confirmacao) return 'As senhas não conferem.';
  return null;
}

// ------------------------------------------------------------------ Entrar
function formEntrar(irParaSenha) {
  const login = entrada({ autocomplete: 'username', required: true, autocapitalize: 'none', spellcheck: 'false' });
  const senha = entrada({ type: 'password', autocomplete: 'current-password', required: true });
  const erro = areaErro();
  const enviar = botao('Entrar', { variante: 'primario', icone: 'log-in', tipo: 'submit', classe: 'btn-bloco' });
  const form = el('form', { class: 'form-login', novalidate: true },
    tituloForm(null, 'Acessar o sistema'), erro,
    campo('Login', login), campo('Senha', senha), enviar,
    el('button', { type: 'button', class: 'btn btn-link', onclick: irParaSenha, text: 'Esqueci minha senha' }));
  form.addEventListener('submit', (ev) => {
    ev.preventDefault();
    erro.limpar();
    if (!login.value.trim() || !senha.value) {
      erro.mostrar({ mensagens: ['Informe o login e a senha.'] });
      return;
    }
    ocupado(enviar, async () => {
      const cred = codificarCredencial(login.value.trim(), senha.value);
      try {
        const usuario = await get('/auth/me', { credencial: cred, login: true });
        if (!salvarSessao(cred, usuario)) {
          erro.mostrar({ mensagens: ['O navegador bloqueou o armazenamento desta aba; libere-o para entrar.'] });
          return;
        }
        location.replace(destino);
      } catch (e) {
        // RN-USU-07 (mesma mensagem para login ou senha errados), RN-USU-04 (bloqueado), RN-USU-08 (tentativas).
        erro.mostrar(e);
        senha.value = '';
        senha.focus();
      }
    });
  });
  form.login = login;
  return form;
}

// ------------------------------------------------------------------ Cadastre-se (ativação da conta)
function formCadastro(aoConcluir) {
  const cpf = mascarar(entrada({ inputmode: 'numeric', placeholder: '000.000.000-00', autocomplete: 'off' }), 'cpf');
  const login = entrada({ autocomplete: 'username', autocapitalize: 'none', spellcheck: 'false', maxlength: 150 });
  const senha = entrada({ type: 'password', autocomplete: 'new-password' });
  const confirmar = entrada({ type: 'password', autocomplete: 'new-password' });
  const pergunta = selecao(PERGUNTAS_SEGURANCA, { vazio: 'Selecione uma pergunta' });
  const resposta = entrada({ maxlength: 150, autocomplete: 'off' });
  const erro = areaErro();
  const enviar = botao('Cadastrar', { variante: 'primario', tipo: 'submit', classe: 'btn-bloco', icone: 'user-plus' });
  const form = el('form', { class: 'form-login compacto', novalidate: true },
    tituloForm('user-plus', 'Criar acesso'),
    el('p', { class: 'card-desc', text: 'Para funcionários já cadastrados pela Fênix: confirme seu CPF e crie seu login. O acesso nasce com o perfil Operador.' }),
    erro,
    campo('CPF', cpf),
    campo('Login', login, { dica: 'Será gravado em minúsculas e sem espaços.' }),
    el('div', { class: 'grade sm-2 grade-3' }, campo('Senha', senha), campo('Confirmar senha', confirmar)),
    campo('Pergunta de segurança', pergunta),
    campo('Resposta', resposta, { dica: 'Usada no lembrete de senha. Maiúsculas e espaços nas pontas não contam.' }),
    enviar);
  form.addEventListener('submit', (ev) => {
    ev.preventDefault();
    erro.limpar();
    const problema = validarSenhaNova(senha.value, confirmar.value);
    if (problema) { erro.mostrar({ mensagens: [problema] }); return; }
    ocupado(enviar, async () => {
      try {
        const u = await post('/auth/ativar-conta', {
          cpf: soDigitos(cpf.value), login: login.value.trim(), senha: senha.value,
          perguntaSeguranca: pergunta.value || null, respostaSeguranca: resposta.value || null,
        }, { publica: true });
        sucesso('Acesso criado!', { detalhe: `Entre com o login ${u.login}.` });
        aoConcluir(u.login);
        form.reset();
      } catch (e) {
        erro.mostrar(e);
      }
    });
  });
  return form;
}

// ------------------------------------------------------------------ Lembrete de senha
function formSenha(aoConcluir) {
  const area = el('div');
  let loginEscolhido = '';

  const passo1 = () => {
    const login = entrada({ autocomplete: 'username', autocapitalize: 'none', spellcheck: 'false', value: loginEscolhido });
    const erro = areaErro();
    const continuar = botao('Continuar', { variante: 'padrao', tipo: 'submit', classe: 'btn-bloco' });
    const form = el('form', { class: 'form-login', novalidate: true },
      tituloForm('key-round', 'Lembrete de senha'),
      el('p', { class: 'card-desc', text: 'Informe seu login para responder à pergunta de segurança.' }),
      erro, campo('Login', login), continuar);
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      erro.limpar();
      if (!login.value.trim()) { erro.mostrar({ mensagens: ['Informe o login.'] }); return; }
      ocupado(continuar, async () => {
        try {
          const r = await get('/auth/pergunta-seguranca?login=' + encodeURIComponent(login.value.trim()), { publica: true });
          loginEscolhido = login.value.trim();
          passo2(r.pergunta);
        } catch (e) {
          erro.mostrar(e);
        }
      });
    });
    trocar(area, form);
    login.focus();
  };

  const passo2 = (pergunta) => {
    const resposta = entrada({ autocomplete: 'off' });
    const nova = entrada({ type: 'password', autocomplete: 'new-password' });
    const confirmar = entrada({ type: 'password', autocomplete: 'new-password' });
    const erro = areaErro();
    const redefinir = botao('Redefinir senha', { variante: 'primario', tipo: 'submit', classe: 'flex-1' });
    const form = el('form', { class: 'form-login', novalidate: true },
      tituloForm('shield-question-mark', 'Pergunta de segurança'),
      el('div', { class: 'caixa-destaque', text: pergunta }),
      erro,
      campo('Sua resposta', resposta),
      el('div', { class: 'grade sm-2 grade-3' }, campo('Nova senha', nova), campo('Confirmar', confirmar)),
      el('div', { class: 'linha' }, botao('Voltar', { classe: 'flex-1', onclick: passo1 }), redefinir));
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      erro.limpar();
      const problema = validarSenhaNova(nova.value, confirmar.value);
      if (problema) { erro.mostrar({ mensagens: [problema] }); return; }
      ocupado(redefinir, async () => {
        try {
          await post('/auth/redefinir-senha', { login: loginEscolhido, respostaSeguranca: resposta.value, novaSenha: nova.value }, { publica: true });
          sucesso('Senha redefinida!', { detalhe: 'Entre com a nova senha.' });
          aoConcluir(loginEscolhido);
          passo1();
        } catch (e) {
          erro.mostrar(e); // RN-USU-05: "Resposta incorreta."; RN-USU-08: muitas tentativas
        }
      });
    });
    trocar(area, form);
    resposta.focus();
  };

  passo1();
  return area;
}

// ------------------------------------------------------------------ montagem
let controleAbas;
const entrar = formEntrar(() => controleAbas.ativar('senha'));
const irParaEntrar = (login) => {
  controleAbas.ativar('entrar');
  if (login) entrar.login.value = login;
  entrar.querySelector('input[type=password]')?.focus();
};
controleAbas = abas([
  { id: 'entrar', rotulo: 'Entrar', conteudo: entrar },
  { id: 'cadastro', rotulo: 'Cadastre-se', conteudo: () => formCadastro(irParaEntrar) },
  { id: 'senha', rotulo: 'Senha', conteudo: () => formSenha(irParaEntrar) },
], { larguraTotal: true });

const avisoMotivo = (() => {
  const motivo = parametros.get('motivo');
  if (motivo === 'expirada') return caixaInfo('Sessão expirada ou acesso não autorizado. Entre novamente.', { tipo: 'aviso', nomeIcone: 'triangle-alert' });
  return null;
})();

trocar(raiz, el('div', { class: 'tela-centro' }, el('div', { class: 'login-caixa' },
  el('div', { class: 'login-marca' },
    el('div', { class: 'logo-caixa logo-20' }, el('img', { src: 'img/fenix-logo.png', alt: 'Fênix', width: 80, height: 80 })),
    el('h1', { text: 'FÊNIX' }),
    el('p', { text: 'Gestão de Ordens de Serviço' })),
  avisoMotivo ? el('div', { class: 'mb-4' }, avisoMotivo) : null,
  el('div', { class: 'card' }, el('div', { class: 'card-p6' }, controleAbas.elemento)),
  el('p', { class: 'login-rodape', text: 'Metalúrgica Fênix · acesso restrito a funcionários' }))));
raiz.classList.remove('app-carregando');
entrar.login.focus();

if (parametros.get('motivo') === 'saiu') informar('Você saiu do sistema.');
