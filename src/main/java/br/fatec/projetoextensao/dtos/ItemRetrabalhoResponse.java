package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * INCLUÍDO (21/10/2026). Item executado no período com alguma etapa repetida (base do % de retrabalho, RN-IRP-09).
 */
public record ItemRetrabalhoResponse(int itemCodigo, String descricao, int osNumero, String clienteNome, LocalDateTime dataFinalizacao,
        List<String> etapasRepetidas) {
}
