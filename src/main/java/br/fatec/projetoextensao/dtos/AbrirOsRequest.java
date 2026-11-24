package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * Abertura de OS com os itens, na mesma transação.
 * prioridade vazia = prioridade do cliente. Número da OS, usuário, data de entrada e valor não vêm no corpo.
 */

public record AbrirOsRequest(Integer clienteCodigo, Integer prioridade, List<ItemRequest> itens) {
}
