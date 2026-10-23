// Campos de pessoa e endereço (INCLUÍDO em 21/10/2026), usados por Clientes, Funcionários e Fornecedores.
//
// Herança/especialização (regras do grupo, garantidas no back-end): não existe cadastro solto de "pessoa"; ela
// nasce como cliente, funcionário ou fornecedor. A mesma pessoa pode ser cliente e funcionário (mesmo CPF) ou
// cliente e fornecedor (mesmo CNPJ); funcionário e fornecedor nunca são a mesma pessoa; cliente é PF ou PJ, nunca
// os dois. Estes campos são o PessoaRequest (nome, e-mail, telefone e endereço) que os três cadastros enviam.

import { UFS } from '../config.js';
import { formatarCep, formatarTelefone, mascarar, soDigitos, textoOuNulo } from '../formato.js';
import { campo, el, entrada, selecao } from '../ui.js';
import { buscarCep } from '../viacep.js';

/** Endereço (AddressFields do protótipo) com a busca pelo CEP. Devolve { elemento, valor() }. */
export function camposEndereco({ valor = {}, somenteLeitura = false } = {}) {
  const v = valor || {};
  const cep = entrada({ placeholder: '00000-000', inputmode: 'numeric', value: formatarCep(v.cep), readOnly: somenteLeitura, autocomplete: 'postal-code' });
  mascarar(cep, 'cep');
  const uf = selecao(UFS, { valor: v.uf ?? '', vazio: 'UF', disabled: somenteLeitura });
  const cidade = entrada({ value: v.cidade ?? '', readOnly: somenteLeitura, maxlength: 60, autocomplete: 'address-level2' });
  const logradouro = entrada({ placeholder: 'Av. / Rua / Travessa', value: v.logradouro ?? '', readOnly: somenteLeitura, maxlength: 100, autocomplete: 'address-line1' });
  const numero = entrada({ placeholder: '123 ou S/N', value: v.nrImovel ?? '', readOnly: somenteLeitura, maxlength: 10 });
  const situacao = el('p', { class: 'dica c6', hidden: true });

  let ultimoBuscado = soDigitos(v.cep);
  cep.addEventListener('input', async () => {
    const digitos = soDigitos(cep.value);
    if (somenteLeitura || digitos.length !== 8 || digitos === ultimoBuscado) return;
    ultimoBuscado = digitos;
    situacao.hidden = false;
    situacao.className = 'dica c6';
    situacao.textContent = 'Buscando o endereço pelo CEP...';
    try {
      const r = await buscarCep(digitos);
      if (soDigitos(cep.value) !== digitos) return;
      if (r.uf) uf.value = r.uf;
      if (r.cidade) cidade.value = r.cidade;
      if (r.logradouro) logradouro.value = r.logradouro;
      situacao.textContent = 'Endereço preenchido pelo CEP. Confira e informe o número.';
      numero.focus();
    } catch (e) {
      situacao.className = 'dica c6 texto-alerta';
      situacao.textContent = e.message;
    }
  });

  const elemento = el('div', { class: 'form-grade c6' },
    campo('CEP', cep, { largura: 'c2', obrigatorio: !somenteLeitura }),
    campo('UF', uf, { largura: 'c1', obrigatorio: !somenteLeitura }),
    campo('Cidade', cidade, { largura: 'c3', obrigatorio: !somenteLeitura }),
    campo('Logradouro (rua)', logradouro, { largura: 'c5', obrigatorio: !somenteLeitura }),
    campo('Nº', numero, { largura: 'c1' }),
    situacao);

  return {
    elemento,
    valor: () => ({
      uf: uf.value || null,
      cidade: textoOuNulo(cidade.value),
      cep: soDigitos(cep.value) || null,
      logradouro: textoOuNulo(logradouro.value),
      nrImovel: textoOuNulo(numero.value),
    }),
  };
}

/**
 * Nome, e-mail, telefone e endereço. rotuloNome muda para "Razão social" em PJ e fornecedor.
 * Devolve { nome, email, telefone, endereco, valor() } — os campos para montar o formulário e o PessoaRequest.
 */
export function camposPessoa({ valor = {}, somenteLeitura = false, rotuloNome = 'Nome completo', larguraNome = 'c4' } = {}) {
  const v = valor || {};
  const nome = entrada({ value: v.nome ?? '', readOnly: somenteLeitura, maxlength: 200, autocomplete: 'off' });
  const email = entrada({ type: 'email', value: v.email ?? '', readOnly: somenteLeitura, maxlength: 150, autocomplete: 'off', placeholder: 'nome@empresa.com' });
  const telefone = entrada({ value: formatarTelefone(v.telefone), readOnly: somenteLeitura, inputmode: 'tel', placeholder: '(00) 00000-0000' });
  mascarar(telefone, 'telefone');
  const endereco = camposEndereco({ valor: v.endereco, somenteLeitura });
  return {
    nome: campo(rotuloNome, nome, { largura: larguraNome, obrigatorio: !somenteLeitura }),
    email: campo('E-mail', email, { largura: 'c3' }),
    telefone: campo('Telefone', telefone, { largura: 'c3', dica: somenteLeitura ? undefined : 'Com DDD (10 ou 11 dígitos).' }),
    endereco: endereco.elemento,
    entradaNome: nome,
    valor: () => ({
      nome: textoOuNulo(nome.value),
      email: textoOuNulo(email.value),
      telefone: soDigitos(telefone.value) || null,
      endereco: endereco.valor(),
    }),
  };
}

/** Selos com os papéis da pessoa (Cliente PF/PJ, Funcionário, Fornecedor), exceto o da própria tela. */
export function selosPapeis(papeis, ignorar = []) {
  const outros = (papeis || []).filter((p) => !ignorar.includes(p));
  if (!outros.length) return null;
  return el('span', { class: 'linha' }, outros.map((p) => el('span', { class: 'badge badge-info-contorno badge-10', text: `Também ${p.toLowerCase().replace('cliente pf', 'cliente PF').replace('cliente pj', 'cliente PJ')}` })));
}

/** Texto curto que explica as regras de especialização (aparece nos formulários de cadastro). */
export const TEXTO_HERANCA = 'A mesma pessoa pode ser cliente e funcionário (mesmo CPF) ou cliente e fornecedor (mesmo CNPJ); '
  + 'funcionário e fornecedor nunca são a mesma pessoa. Ao usar um documento já cadastrado, o sistema liga o novo papel à pessoa existente.';
