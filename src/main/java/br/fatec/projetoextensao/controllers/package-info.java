/**
 * INCLUÍDO (21/10/2026): rotas REST da API (/api/...), usadas pelo front em src/main/resources/static.
 *
 * Os Controllers só traduzem HTTP para os Services: validação, regra de negócio, perfil exigido
 * (@PreAuthorize ADMIN ou ADMIN/OPERADOR) e transação ficam no Service. Erros viram JSON no formato ErroResponse
 * pelo GlobalExceptionHandler (RN-SIS-07): 400 regra violada, 401 sem login, 403 sem perfil, 404 não encontrado,
 * 409 conflito (duplicado ou em uso), 413 arquivo grande demais.
 *
 * Em alteração, o código do registro vem da URL e nunca do corpo (RN-SIS-05). Inclusão responde 201 com o registro
 * criado; exclusão e operações sem retorno respondem 204.
 */
package br.fatec.projetoextensao.controllers;
