package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Abertura de OS com os itens, na mesma transação (RN-OS-01, RN-SIS-04).
 * prioridade vazia = prioridade do cliente (RN-OS-17). Número da OS (RN-OS-08), usuário, data de entrada e valor não vêm no
 * corpo (RN-SIS-05).
 */
public record AbrirOsRequest(Integer clienteCodigo, Integer prioridade, List<ItemRequest> itens) {
}
