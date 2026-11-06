// Configurações (INCLUÍDO em 21/10/2026) — só ADMIN (RN-SIS-03: alterar as listas de apoio é do Administrador).
// O protótipo tinha as abas Empresa, Conta, Acessos e Notificações, mas o banco não guarda dados da empresa nem
// preferências de notificação; "Conta" virou a tela Meu perfil. Aqui ficam as listas de apoio (cores, fontes de
// aquisição e etapas da rota), os parâmetros do sistema (só leitura) e o resumo dos perfis de acesso.

import { del, get, post, put } from '../api.js';
import { normalizar } from '../formato.js';
import { montarPagina } from '../layout.js';
import * as ref from '../referencias.js';
import {
  abas, badge, botao, botaoIcone, cabecalho, caixaInfo, cartao, confirmar, el, entrada, esqueleto, icone, mostrarErro,
  ocupado, pedirTexto, sucesso, tabela, trocar,
} from '../ui.js';

const pagina = await montarPagina({ pagina: 'configuracoes', titulo: 'Configurações', somenteAdmin: true });

if (pagina) {
  const { conteudo } = pagina;

  /** Card de uma lista de apoio (CRUD de nome): POST, PUT /{codigo} e DELETE /{codigo}. */
  function cardLista({ titulo, descricao, caminho, chaveCache, nomeIcone, dica, protegidos = [], placeholder }) {
    const lista = el('div', { class: 'pilha-2' }, esqueleto(3));
    const novoNome = entrada({ placeholder, maxlength: 100 });
    const adicionar = botao('Adicionar', { variante: 'primario', icone: 'plus', tipo: 'submit' });
    let itens = [];
    const duplicado = (nome, ignorar) => itens.some((i) => i.codigo !== ignorar && normalizar(i.nome).replace(/\s+/g, ' ') === normalizar(nome).replace(/\s+/g, ' '));

    async function carregar() {
      try {
        ref.esquecer(chaveCache);
        itens = await get(caminho);
        trocar(lista, itens.length ? itens.map((i) => {
          const protegido = protegidos.some((p) => normalizar(p) === normalizar(i.nome));
          return el('div', { class: 'item-lista claro' },
            el('span', { class: 'linha' }, el('span', { class: 'medio', text: i.nome }), protegido ? badge('usada pelo sistema', 'contorno', 'lock') : null),
            protegido ? null : el('div', { class: 'linha' },
              botaoIcone('pencil', 'Renomear', () => renomear(i)),
              botaoIcone('trash-2', 'Excluir', (ev) => excluir(ev.currentTarget, i))));
        }) : el('p', { class: 'texto-sm texto-mudo', text: 'Nenhum registro.' }));
      } catch (e) {
        trocar(lista);
        mostrarErro(e);
      }
    }

    async function renomear(i) {
      const nome = await pedirTexto({
        titulo: `Renomear "${i.nome}"`, rotulo: 'Novo nome', valor: i.nome, dica,
        validar: (v) => (duplicado(v, i.codigo) ? 'Já existe um registro com este nome (mesmo com outra grafia).' : null),
      });
      if (nome === null) return;
      try {
        await put(`${caminho}/${i.codigo}`, { nome });
        sucesso('Nome alterado.');
        await carregar();
      } catch (e) {
        mostrarErro(e);
      }
    }

    function excluir(b, i) {
      confirmar(`Excluir "${i.nome}"? Registro em uso não é excluído (RN-SIS-12).`, { titulo: 'Excluir', textoConfirmar: 'Excluir', perigo: true }).then((ok) => {
        if (!ok) return;
        ocupado(b, async () => {
          try {
            await del(`${caminho}/${i.codigo}`);
            sucesso('Excluído.');
            await carregar();
          } catch (e) {
            mostrarErro(e);
          }
        });
      });
    }

    const form = el('form', { class: 'linha', novalidate: true }, el('div', { class: 'flex-1' }, novoNome), adicionar);
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      const nome = novoNome.value.trim();
      if (!nome) { mostrarErro({ mensagens: ['Informe o nome.'] }); return; }
      if (duplicado(nome)) { mostrarErro({ mensagens: ['Já existe um registro com este nome (mesmo com outra grafia).'] }); return; }
      ocupado(adicionar, async () => {
        try {
          await post(caminho, { nome });
          novoNome.value = '';
          sucesso(`"${nome}" adicionado.`);
          await carregar();
        } catch (e) {
          mostrarErro(e);
        }
      });
    });
    carregar();
    const card = cartao({ titulo, descricao, corpo: el('div', { class: 'pilha-4' }, form, dica ? el('p', { class: 'dica', text: dica }) : null, lista) });
    card.querySelector('.card-titulo').prepend(icone(nomeIcone, 'texto-primario'));
    card.querySelector('.card-titulo').classList.add('linha');
    return card;
  }

  const abaListas = () => el('div', { class: 'grade lg-3' },
    cardLista({
      titulo: 'Cores', descricao: 'Cor pedida no item e cor da tinta.', caminho: '/cores', chaveCache: 'cores', nomeIcone: 'palette',
      placeholder: 'Ex.: Vermelho RAL 3020', dica: 'Padronize o nome, com o código RAL quando houver, para não repetir a mesma cor com grafias diferentes (RN-COR-04).',
    }),
    cardLista({
      titulo: 'Fontes de aquisição', descricao: 'Como o cliente conheceu a Fênix.', caminho: '/fontes', chaveCache: 'fontes', nomeIcone: 'sparkles',
      placeholder: 'Ex.: Indicação',
    }),
    cardLista({
      titulo: 'Etapas da rota', descricao: 'Etapas do processo (Jateamento, Banho, Pintura...).', caminho: '/etapas', chaveCache: 'etapas', nomeIcone: 'route',
      placeholder: 'Ex.: Jateamento', protegidos: ['Pintura'], dica: 'A etapa Pintura é usada pelo sistema (exige cor e tinta no item) e não pode ser renomeada nem excluída.',
    }));

  const abaParametros = () => {
    const area = el('div', {}, esqueleto(5));
    ref.parametros().then((p) => {
      const linhas = [
        ['Escala de prioridade (cliente e OS)', `${p.prioridadeMinima} a ${p.prioridadeMaxima}`, 'fenix.prioridade.minima / maxima', 'RN-CLI-09 — definida em 22/10'],
        ['Prioridade do cliente', 'Calculada: faturamento do cliente ÷ maior faturamento × 100 (OS canceladas não contam)', 'PrioridadeClienteService', 'RN-CLI-09'],
        ['Unidades do item', p.unidades.join(', '), 'fenix.itens.unidades', 'RN-ITE-17 — definida em 22/10'],
        ['Fotos por item', String(p.maxFotosPorItem), 'fenix.fotos.max-por-item', 'decisão de 20/10'],
        ['Tamanho máximo da foto', `${p.tamanhoMaximoFotoMb} MB`, 'fenix.fotos.tamanho-maximo-mb', 'RN-FOT'],
        ['Unidade do estoque de tinta', p.unidadeEstoqueTinta, 'TintaService.UNIDADE_ESTOQUE', 'RN-TIN-15'],
        ['Tamanho da senha', `${p.senhaMinimo} a ${p.senhaMaximo} caracteres`, 'UsuarioService.SENHA_MINIMO / MAXIMO', 'RN-USU-15'],
        ['Cargos', p.cargos.join(', '), 'FuncionarioService.CARGOS', 'RN-FUN-07'],
        ['Prazos de pagamento', p.prazosPagamento.join(', '), 'FornecedorService.PRAZOS_PAGAMENTO', 'RN-FOR-09'],
      ];
      trocar(area, el('div', { class: 'pilha-4' },
        caixaInfo('Parâmetros só de leitura nesta tela: mudam no application.properties (fenix.*) ou nas listas fixas dos Services, e a tela acompanha sem ser editada.'),
        el('div', { class: 'card card-tabela' }, tabela({
          colunas: [{ titulo: 'Parâmetro' }, { titulo: 'Valor atual' }, { titulo: 'Onde muda' }, { titulo: 'Regra' }],
          linhas,
          montar: (l) => [el('span', { class: 'medio', text: l[0] }), l[1], el('span', { class: 'mono texto-xs texto-mudo', text: l[2] }), el('span', { class: 'texto-xs texto-mudo', text: l[3] })],
        }))));
    }).catch((e) => { trocar(area); mostrarErro(e); });
    return area;
  };

  const abaAcessos = () => {
    const area = el('div', { class: 'pilha-4' }, esqueleto(3));
    get('/usuarios').then((usuarios) => {
      const admins = usuarios.filter((u) => u.admin && u.ativo).length;
      const operadores = usuarios.filter((u) => !u.admin && u.ativo).length;
      const bloqueados = usuarios.filter((u) => !u.ativo).length;
      const permissoes = [
        ['Ver painel, OS, peças, produção, cronograma e cadastros', true, true],
        ['Abrir OS, incluir e alterar itens, fotos e etapas', true, true],
        ['Cadastrar e alterar clientes, fornecedores e tintas; ajustar estoque', true, true],
        ['Cadastrar e alterar funcionários; ver salário', true, false],
        ['Excluir cadastros; inativar fornecedor; corrigir CPF/CNPJ', true, false],
        ['Cancelar OS; corrigir datas de etapa', true, false],
        ['Listas de apoio (cores, fontes, etapas); perfis e bloqueio de usuários', true, false],
        ['Alterar o próprio perfil, senha e pergunta de segurança', true, true],
      ];
      trocar(area,
        el('div', { class: 'grade sm-3' },
          cartao({ titulo: 'Administradores ativos', corpo: el('div', { class: 'numero-grande', text: String(admins) }) }),
          cartao({ titulo: 'Operadores ativos', corpo: el('div', { class: 'numero-grande texto-info', text: String(operadores) }) }),
          cartao({ titulo: 'Acessos bloqueados', corpo: el('div', { class: 'numero-grande texto-mudo', text: String(bloqueados) }) })),
        cartao({
          titulo: 'Controle de acesso', descricao: 'Perfis: Administrador (acesso total) e Operador (operação do dia a dia). Quem garante é o back-end (@PreAuthorize).',
          acoes: el('a', { class: 'btn btn-contorno btn-sm', href: 'funcionarios.html' }, icone('users'), el('span', { text: 'Gerenciar usuários' })),
          corpo: tabela({
            colunas: [{ titulo: 'Ação' }, { titulo: 'Administrador', classe: 'centro' }, { titulo: 'Operador', classe: 'centro' }],
            linhas: permissoes,
            montar: (p) => [p[0], { conteudo: p[1] ? icone('check', 'texto-sucesso') : icone('x', 'texto-mudo'), classe: 'centro' }, { conteudo: p[2] ? icone('check', 'texto-sucesso') : icone('x', 'texto-mudo'), classe: 'centro' }],
          }),
        }));
      area.querySelectorAll('td.centro .icone').forEach((i) => i.classList.add('centro-bloco'));
    }).catch((e) => { trocar(area); mostrarErro(e); });
    return area;
  };

  conteudo.append(el('div', { class: 'pilha' },
    cabecalho('Configurações', 'Listas de apoio, parâmetros e permissões do sistema.'),
    abas([
      { id: 'listas', rotulo: 'Listas de apoio', icone: 'list', conteudo: abaListas },
      { id: 'parametros', rotulo: 'Parâmetros', icone: 'sliders-horizontal', conteudo: abaParametros },
      { id: 'acessos', rotulo: 'Acessos', icone: 'shield-check', conteudo: abaAcessos },
    ]).elemento));
}
