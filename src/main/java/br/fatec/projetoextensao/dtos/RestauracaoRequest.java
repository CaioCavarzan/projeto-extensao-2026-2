package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (18/11/2026) [E3-19]: pedido de restore (POST /api/banco/restauracao). confirmacao precisa ser exatamente
 * o texto pedido na tela (BancoDadosService.CONFIRMACAO_RESTORE): o restore substitui o banco inteiro pelo último
 * backup e desconecta todos os usuários, então não pode acontecer por um clique sem querer.
 */
public record RestauracaoRequest(String confirmacao) {
}
