package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * S devolvida pela API. valorIncompleto = há item ativo sem valor.
 */

public record OsResponse(
        int numero,
        int clienteCodigo,
        String clienteNome,
        String registradaPor,
        LocalDateTime dataEntrada,
        BigDecimal valorOs,
        boolean valorIncompleto,
        Integer prioridade,
        String status,
        List<ItemResponse> itens,
        List<String> avisos) {
}
