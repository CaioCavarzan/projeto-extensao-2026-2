package br.fatec.projetoextensao.dtos;

/**
 * Pedido de restore (POST /api/banco/restauracao). confirmacao precisa ser exatamente
 * o texto pedido na tela (BancoDadosService.CONFIRMACAO_RESTORE): o restore substitui o banco inteiro pelo último
 * backup e desconecta todos os usuários, então não pode acontecer por um clique sem querer.
 */

public record RestauracaoRequest(String confirmacao) {
}
