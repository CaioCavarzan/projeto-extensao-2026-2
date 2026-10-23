// Funcionários e usuários (INCLUÍDO em 21/10/2026), no layout do funcionarios.tsx do protótipo.
//
// Permissões (RN-SIS-03): cadastrar, alterar, desligar e excluir funcionário é só do ADMIN; o OPERADOR vê a equipe
// sem o salário (RN-FUN-09: o back-end nem envia o salário para ele). A aba Usuários (perfis e bloqueio) é só do ADMIN.
// Especialização: o funcionário pode ser também cliente PF (mesmo CPF); funcionário nunca é fornecedor.
// Acesso ao sistema: o próprio funcionário ativa a conta em "Cadastre-se" (RN-USU-02); não há "novo usuário" aqui.

import { del, get, post, put } from '../api.js';
import { camposPessoa, selosPapeis, TEXTO_HERANCA } from '../componentes/pessoa.js';
import { data, decimalParaCampo, formatarCpf, hojeISO, iniciais, lerDecimal, mascarar, soDigitos } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  abas, avisoAcesso, badge, botao, cabecalho, caixaInfo, campo, confirmar, dialogo, el, entrada, esqueleto,
  mostrarAvisos, mostrarErro, ocupado, pedirTexto, selecao, sucesso, tabela, trocar, vazio,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'funcionarios', titulo: 'Funcionários' });

if (pagina) {
  const { conteudo, admin, usuario } = pagina;
  let funcionarios = [];
  let parametros = null;
  const gradeFuncionarios = el('div', { class: 'grade sm-2 lg-3' }, esqueleto(1, 'esqueleto-bloco'));
  const areaUsuarios = el('div', {}, esqueleto(4));

  function seloAcesso(f) {
    if (!f.temAcesso) return badge('Sem acesso', 'contorno');
    return f.acessoAtivo ? badge('Acesso ativo', 'sucesso') : badge('Acesso bloqueado', 'mudo');
  }

  function desenharFuncionarios() {
    trocar(gradeFuncionarios, funcionarios.length ? funcionarios.map((f) => el('div', { class: 'card clicavel', role: 'button', tabindex: 0, onclick: () => dialogoVer(f), onkeydown: (ev) => { if (ev.key === 'Enter') dialogoVer(f); } },
      el('div', { class: 'card-p5' },
        el('div', { class: 'linha-topo' },
          el('span', { class: 'avatar avatar-14 avatar-gradiente', text: iniciais(f.nome) }),
          el('div', { class: 'flex-1' },
            el('div', { class: 'linha-entre' }, el('h3', { class: 'seminegrito truncar', text: f.nome }), seloAcesso(f)),
            el('p', { class: 'texto-xs texto-mudo mt-1', text: f.cargo }),
            selosPapeis(f.papeis, ['Funcionário']))),
        el('div', { class: 'divisor-topo mt-4 linha-entre texto-xs' },
          el('span', { class: 'texto-mudo', text: 'Admissão' }), el('span', { class: 'seminegrito texto-primario', text: data(f.dataAdmissao) })))))
      : el('div', { class: 'card sm-span-6' }, vazio('Nenhum funcionário cadastrado.', 'hard-hat')));
  }

  // ---------------------------------------------------------------- formulário
  function montarFormulario(f = null) {
    const novo = !f;
    const somenteLeitura = !admin;
    const pessoa = camposPessoa({ valor: f ? { nome: f.nome, email: f.email, telefone: f.telefone, endereco: f.endereco } : {}, somenteLeitura });
    const cpf = mascarar(entrada({ inputmode: 'numeric', placeholder: '000.000.000-00', value: f ? formatarCpf(f.cpf) : '', readOnly: !novo }), 'cpf');
    const cargo = selecao(parametros.cargos, { vazio: 'Selecione', valor: f?.cargo ?? '', disabled: somenteLeitura });
    const salario = entrada({ inputmode: 'decimal', placeholder: '0,00', value: decimalParaCampo(f?.salario), readOnly: somenteLeitura });
    const admissao = entrada({ type: 'date', value: f?.dataAdmissao ?? (novo ? hojeISO() : ''), max: hojeISO(), readOnly: somenteLeitura });
    const campoCpf = campo('CPF', cpf, { largura: 'c2', obrigatorio: novo });
    if (!novo && admin) campoCpf.append(botao('Corrigir CPF', { tamanho: 'sm', variante: 'fantasma', icone: 'pencil', onclick: () => corrigirCpf(f) }));
    const elemento = el('div', { class: 'pilha' },
      el('div', { class: 'form-grade' },
        pessoa.nome, campoCpf, pessoa.email, pessoa.telefone,
        campo('Cargo', cargo, { largura: 'c2', obrigatorio: !somenteLeitura, dica: somenteLeitura ? undefined : 'Lista padronizada (RN-FUN-07).' }),
        admin ? campo('Salário (R$)', salario, { largura: 'c2', obrigatorio: true, dica: 'Confidencial: só o Administrador vê (RN-FUN-09).' }) : null,
        campo('Data de admissão', admissao, { largura: 'c2', obrigatorio: !somenteLeitura })),
      el('section', { class: 'secao-form' }, el('h3', { class: 'secao-titulo', text: 'Endereço' }), pessoa.endereco),
      novo ? caixaInfo(TEXTO_HERANCA) : null);
    return {
      elemento,
      erros: () => (Number.isNaN(lerDecimal(salario.value)) ? ['Salário inválido: use números, com vírgula para os centavos.'] : []),
      valor() {
        const base = { pessoa: pessoa.valor(), cargo: cargo.value || null, salario: lerDecimal(salario.value), dataAdmissao: admissao.value || null };
        return novo ? { ...base, cpf: soDigitos(cpf.value) } : base;
      },
    };
  }

  async function dialogoNovo() {
    parametros = parametros || await ref.parametros(); // o botão pode ser clicado antes de a lista carregar
    const form = montarFormulario();
    const salvar = botao('Cadastrar', { variante: 'primario', icone: 'save' });
    const dlg = dialogo({ titulo: 'Cadastrar novo funcionário', conteudo: form.elemento, largura: '3xl', botoes: [botao('Cancelar', { onclick: () => dlg.fechar() }), salvar] });
    salvar.addEventListener('click', () => ocupado(salvar, async () => {
      const erros = form.erros();
      if (erros.length) { dlg.mostrarErro({ mensagens: erros }); return; }
      try {
        const f = await post('/funcionarios', form.valor());
        dlg.fechar();
        sucesso(`Funcionário ${f.nome} cadastrado.`, { detalhe: 'Para usar o sistema, ele ativa o acesso em "Cadastre-se", na tela de login, com o CPF.' });
        mostrarAvisos(f.avisos);
        ref.esquecer('funcionarios');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  function dialogoVer(f) {
    const form = montarFormulario(f);
    const botoes = [];
    if (admin) {
      const esquerda = el('div', { class: 'esquerda' });
      if (f.temAcesso && f.acessoAtivo && f.codigo !== usuario.funcionarioCodigo) {
        const desligar = botao('Desligar', { variante: 'perigo-contorno', icone: 'user-x' });
        desligar.addEventListener('click', async () => {
          if (!(await confirmar(`Desligar ${f.nome}? O acesso ao sistema é bloqueado e o cadastro fica para o histórico (RN-FUN-02).`, { titulo: 'Desligar funcionário', textoConfirmar: 'Desligar', perigo: true }))) return;
          await acaoDialogo(desligar, () => post(`/funcionarios/${f.codigo}/desligar`), 'Funcionário desligado: acesso bloqueado.');
        });
        esquerda.append(desligar);
      }
      const excluir = botao('Excluir', { variante: 'perigo-contorno', icone: 'trash-2' });
      excluir.addEventListener('click', async () => {
        if (!(await confirmar(`Excluir ${f.nome}? Funcionário com acesso ou com etapas registradas não é excluído; nesse caso, use "Desligar".`, { titulo: 'Excluir funcionário', textoConfirmar: 'Excluir', perigo: true }))) return;
        await acaoDialogo(excluir, () => del(`/funcionarios/${f.codigo}`), 'Funcionário excluído.');
      });
      esquerda.append(excluir);
      botoes.push(esquerda);
    }
    botoes.push(botao('Fechar', { onclick: () => dlg.fechar() }));
    let salvar = null;
    if (admin) {
      salvar = botao('Salvar alterações', { variante: 'primario', icone: 'save' });
      botoes.push(salvar);
    }
    const dlg = dialogo({
      titulo: admin ? 'Editar funcionário' : 'Visualizar funcionário', largura: '3xl',
      descricao: [f.papeis?.length > 1 ? `Papéis desta pessoa: ${f.papeis.join(' · ')}.` : '', f.temAcesso ? (f.acessoAtivo ? 'Tem acesso ao sistema.' : 'Acesso bloqueado.') : 'Ainda não ativou o acesso ao sistema.'].filter(Boolean).join(' '),
      conteudo: form.elemento, botoes,
    });
    async function acaoDialogo(b, chamada, mensagem) {
      await ocupado(b, async () => {
        try {
          await chamada();
          dlg.fechar();
          sucesso(mensagem);
          ref.esquecer('funcionarios');
          await carregar();
        } catch (e) {
          dlg.mostrarErro(e);
        }
      });
    }
    salvar?.addEventListener('click', () => ocupado(salvar, async () => {
      const erros = form.erros();
      if (erros.length) { dlg.mostrarErro({ mensagens: erros }); return; }
      try {
        const r = await put(`/funcionarios/${f.codigo}`, form.valor());
        dlg.fechar();
        sucesso('Funcionário atualizado.');
        mostrarAvisos(r.avisos);
        ref.esquecer('funcionarios');
        await carregar();
      } catch (e) {
        dlg.mostrarErro(e);
      }
    }));
  }

  async function corrigirCpf(f) {
    const novo = await pedirTexto({
      titulo: 'Corrigir CPF', rotulo: 'Novo CPF', valor: formatarCpf(f.cpf), mascara: (e) => mascarar(e, 'cpf'),
      dica: 'Vale para todos os papéis da pessoa que usam o CPF (funcionário e cliente PF).',
    });
    if (novo === null) return;
    try {
      const r = await put(`/pessoas/${f.codigo}/cpf`, { documento: soDigitos(novo) });
      sucesso('CPF corrigido.');
      mostrarAvisos(r?.mensagens);
      document.querySelectorAll('dialog').forEach((d) => d.close());
      await carregar();
    } catch (e) {
      mostrarErro(e);
    }
  }

  // ---------------------------------------------------------------- usuários (ADMIN)
  async function carregarUsuarios() {
    try {
      const usuarios = await get('/usuarios');
      trocar(areaUsuarios, el('div', { class: 'pilha-4' },
        caixaInfo('Novos acessos: o funcionário cria o próprio login em "Cadastre-se", na tela de login, com o CPF cadastrado na aba Funcionários. O acesso nasce com o perfil Operador (RN-USU-02 e RN-USU-03). Sempre fica pelo menos um Administrador ativo (RN-USU-06).'),
        el('div', { class: 'card card-tabela' }, tabela({
          colunas: [{ titulo: 'Funcionário' }, { titulo: 'Login' }, { titulo: 'Nível de acesso' }, { titulo: 'Status' }, { titulo: 'Ações', classe: 'direita' }],
          linhas: usuarios,
          textoVazio: 'Nenhum funcionário ativou o acesso ainda.',
          montar: (u) => {
            const perfil = botao(u.admin ? 'Tornar operador' : 'Tornar administrador', { tamanho: 'sm', icone: u.admin ? 'user' : 'shield-check' });
            perfil.addEventListener('click', async () => {
              const texto = u.admin ? `${u.nome} passa a ter o perfil Operador.` : `${u.nome} passa a ter o perfil Administrador, com acesso total.`;
              if (!(await confirmar(texto, { titulo: 'Alterar perfil', textoConfirmar: 'Alterar' }))) return;
              await acaoUsuario(perfil, () => put(`/usuarios/${u.funcionarioCodigo}/perfil?admin=${!u.admin}`), 'Perfil alterado.');
            });
            const bloqueio = botao(u.ativo ? 'Bloquear' : 'Desbloquear', { tamanho: 'sm', variante: u.ativo ? 'perigo-contorno' : 'contorno', icone: u.ativo ? 'lock' : 'user-check' });
            bloqueio.addEventListener('click', async () => {
              if (u.ativo && !(await confirmar(`Bloquear o acesso de ${u.nome}? Ele não entra no sistema nem com a senha certa (RN-USU-04).`, { titulo: 'Bloquear acesso', textoConfirmar: 'Bloquear', perigo: true }))) return;
              await acaoUsuario(bloqueio, () => post(`/usuarios/${u.funcionarioCodigo}/${u.ativo ? 'bloquear' : 'desbloquear'}`), u.ativo ? 'Acesso bloqueado.' : 'Acesso desbloqueado.');
            });
            const proprio = u.funcionarioCodigo === usuario.funcionarioCodigo;
            return [
              el('div', { class: 'linha-3' }, el('span', { class: 'avatar avatar-9 avatar-gradiente', text: iniciais(u.nome) }), el('span', { class: 'medio', text: u.nome }), proprio ? badge('você', 'contorno') : null),
              el('span', { class: 'mono texto-xs texto-mudo', text: `@${u.login}` }),
              u.admin ? badge('Administrador', 'primario') : badge('Operador', 'contorno'),
              u.ativo ? badge('Ativo', 'sucesso') : badge('Bloqueado', 'mudo'),
              { conteudo: el('div', { class: 'acoes' }, perfil, bloqueio) },
            ];
          },
        }))));
    } catch (e) {
      trocar(areaUsuarios, vazio('Não foi possível carregar os usuários.', 'circle-alert'));
      mostrarErro(e);
    }
  }

  async function acaoUsuario(b, chamada, mensagem) {
    await ocupado(b, async () => {
      try {
        await chamada();
        sucesso(mensagem);
        ref.esquecer('funcionarios');
        await Promise.all([carregarUsuarios(), carregar()]);
      } catch (e) {
        mostrarErro(e);
      }
    });
  }

  // ---------------------------------------------------------------- montagem
  const secaoFuncionarios = el('div', { class: 'pilha-4' },
    admin ? el('div', { class: 'linha', }, el('span', { class: 'ml-auto' }), botao('Novo funcionário', { variante: 'primario', icone: 'plus', onclick: () => dialogoNovo().catch(mostrarErro) })) : null,
    gradeFuncionarios);
  const lista = [{ id: 'funcs', rotulo: 'Funcionários', conteudo: secaoFuncionarios }];
  if (admin) lista.push({ id: 'users', rotulo: 'Usuários', conteudo: () => { carregarUsuarios(); return areaUsuarios; } });
  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Funcionários', admin ? 'Equipe operacional e usuários do sistema.' : 'Equipe operacional da Fênix.'),
    avisoAcesso(admin, 'Somente leitura — apenas Administradores cadastram e alteram funcionários.'),
    abas(lista).elemento));

  async function carregar() {
    try {
      ref.esquecer('funcionarios');
      const [lista2, p] = await Promise.all([ref.funcionarios(), ref.parametros()]);
      funcionarios = [...lista2].sort((a, b) => a.nome.localeCompare(b.nome));
      parametros = p;
      desenharFuncionarios();
    } catch (e) {
      trocar(gradeFuncionarios, el('div', { class: 'card sm-span-6' }, vazio('Não foi possível carregar os funcionários.', 'circle-alert')));
      mostrarErro(e);
    }
  }

  carregar();
}
