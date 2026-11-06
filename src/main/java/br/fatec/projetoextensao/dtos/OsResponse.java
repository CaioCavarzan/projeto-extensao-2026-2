package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * INCLUÍDO (20/10/2026). OS devolvida pela API. valorIncompleto = há item ativo sem valor (RN-OS-15).
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
