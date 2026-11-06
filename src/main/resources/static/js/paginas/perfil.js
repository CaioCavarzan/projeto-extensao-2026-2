// Meu perfil (INCLUÍDO em 21/10/2026) — Etapa 2: "todo usuário faz a alteração do seu perfil".
// No layout do perfil.tsx do protótipo: dados pessoais, senha e pergunta de segurança. O usuário altera os próprios
// contatos (e-mail e telefone); nome, cargo e CPF ficam com o Administrador, no cadastro de funcionários.
// A senha nova passa a valer na hora: a credencial guardada nesta aba é trocada para a nova senha.

import { get, put } from '../api.js';
import { PERGUNTAS_SEGURANCA, SENHA } from '../config.js';
import { formatarTelefone, mascarar, soDigitos, textoOuNulo } from '../formato.js';
import { montarPagina } from '../layout.js';
import { codificarCredencial, salvarSessao } from '../sessao.js';
import { badge, botao, cabecalho, campo, cartao, el, entrada, esqueleto, mostrarErro, ocupado, selecao, sucesso, trocar } from '../ui.js';

const pagina = await montarPagina({ pagina: 'perfil', titulo: 'Meu perfil' });

if (pagina) {
  const { conteudo } = pagina;
  const area = el('div', { class: 'max-w-3xl pilha' }, cabecalho('Meu perfil', 'Todo usuário pode alterar os próprios dados de acesso.'), esqueleto(4, 'esqueleto-bloco'));
  conteudo.append(area);

  try {
    const perfil = await get('/perfil');

    // ---------------------------------------------------------------- dados pessoais
    const email = entrada({ type: 'email', value: perfil.email || '', maxlength: 150 });
    const telefone = mascarar(entrada({ inputmode: 'tel', value: formatarTelefone(perfil.telefone), placeholder: '(00) 00000-0000' }), 'telefone');
    const salvarDados = botao('Salvar dados', { variante: 'primario', tipo: 'submit', icone: 'save' });
    const formDados = el('form', { class: 'form-grade', novalidate: true },
      campo('Nome completo', entrada({ value: perfil.nome, readOnly: true }), { largura: 'c3', dica: 'Alterado pelo Administrador, no cadastro de funcionários.' }),
      campo('Cargo', entrada({ value: perfil.cargo || '—', readOnly: true }), { largura: 'c3' }),
      campo('E-mail', email, { largura: 'c3' }),
      campo('Telefone', telefone, { largura: 'c3', dica: 'Com DDD (10 ou 11 dígitos).' }),
      el('div', { class: 'c6 linha' }, el('span', { class: 'ml-auto' }), salvarDados));
    formDados.addEventListener('submit', (ev) => {
      ev.preventDefault();
      ocupado(salvarDados, async () => {
        try {
          await put('/perfil', { email: textoOuNulo(email.value), telefone: soDigitos(telefone.value) || null });
          sucesso('Dados atualizados.');
        } catch (e) {
          mostrarErro(e);
        }
      });
    });
    const cardDados = cartao({ titulo: 'Dados pessoais', corpo: formDados });
    cardDados.querySelector('.card-titulo').classList.add('linha');
    cardDados.querySelector('.card-titulo').append(badge(`@${perfil.login}`, 'contorno'), perfil.admin ? badge('Administrador', 'primario', 'shield-check') : badge('Operador', 'contorno', 'user'));

    // ---------------------------------------------------------------- senha
    const atual = entrada({ type: 'password', autocomplete: 'current-password' });
    const nova = entrada({ type: 'password', autocomplete: 'new-password' });
    const confirmar = entrada({ type: 'password', autocomplete: 'new-password' });
    const salvarSenha = botao('Alterar senha', { tipo: 'submit', icone: 'key-round' });
    const formSenha = el('form', { class: 'form-grade', novalidate: true },
      campo('Senha atual', atual, { largura: 'c2' }), campo('Nova senha', nova, { largura: 'c2' }), campo('Confirmar', confirmar, { largura: 'c2' }),
      el('div', { class: 'c6 linha' }, el('span', { class: 'ml-auto' }), salvarSenha));
    formSenha.addEventListener('submit', (ev) => {
      ev.preventDefault();
      if (nova.value.length < SENHA.minimo || nova.value.length > SENHA.maximo) { mostrarErro({ mensagens: [`A senha precisa ter de ${SENHA.minimo} a ${SENHA.maximo} caracteres.`] }); return; }
      if (nova.value !== confirmar.value) { mostrarErro({ mensagens: ['As senhas não conferem.'] }); return; }
      ocupado(salvarSenha, async () => {
        try {
          await put('/perfil/senha', { senhaAtual: atual.value, novaSenha: nova.value });
          // A credencial da aba passa a usar a senha nova (o HTTP Basic envia login e senha em toda requisição).
          salvarSessao(codificarCredencial(perfil.login, nova.value), pagina.usuario);
          formSenha.reset();
          sucesso('Senha alterada.');
        } catch (e) {
          mostrarErro(e); // "Senha atual incorreta." etc.
        }
      });
    });

    // ---------------------------------------------------------------- pergunta de segurança
    const opcoes = [...PERGUNTAS_SEGURANCA];
    if (perfil.perguntaSeguranca && !opcoes.includes(perfil.perguntaSeguranca)) opcoes.unshift(perfil.perguntaSeguranca);
    const pergunta = selecao(opcoes, { vazio: 'Selecione uma pergunta', valor: perfil.perguntaSeguranca || '' });
    const resposta = entrada({ maxlength: 150, autocomplete: 'off' });
    const senhaPergunta = entrada({ type: 'password', autocomplete: 'current-password' });
    const salvarPergunta = botao('Salvar pergunta', { tipo: 'submit', icone: 'shield-question-mark' });
    const formPergunta = el('form', { class: 'form-grade', novalidate: true },
      campo('Pergunta', pergunta, { largura: 'c6' }),
      campo('Nova resposta', resposta, { largura: 'c3', dica: 'Maiúsculas e espaços nas pontas não contam (RN-USU-18).' }),
      campo('Senha atual', senhaPergunta, { largura: 'c3', dica: 'Confirma que é você.' }),
      el('div', { class: 'c6 linha' }, el('span', { class: 'ml-auto' }), salvarPergunta));
    formPergunta.addEventListener('submit', (ev) => {
      ev.preventDefault();
      ocupado(salvarPergunta, async () => {
        try {
          await put('/perfil/pergunta-seguranca', { senhaAtual: senhaPergunta.value, perguntaSeguranca: pergunta.value || null, respostaSeguranca: resposta.value || null });
          resposta.value = '';
          senhaPergunta.value = '';
          sucesso('Pergunta de segurança atualizada.');
        } catch (e) {
          mostrarErro(e);
        }
      });
    });

    trocar(area,
      cabecalho('Meu perfil', 'Todo usuário pode alterar os próprios dados de acesso.'),
      cardDados,
      cartao({ titulo: 'Alterar senha', descricao: `Informe a senha atual para definir uma nova (${SENHA.minimo} a ${SENHA.maximo} caracteres).`, corpo: formSenha }),
      cartao({ titulo: 'Pergunta de segurança', descricao: 'Usada no lembrete de senha da tela de login.', corpo: formPergunta }));
  } catch (e) {
    trocar(area, cabecalho('Meu perfil', 'Não foi possível carregar o perfil.'));
    mostrarErro(e);
  }
}
