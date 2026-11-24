package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Item executado no período com alguma etapa repetida (base do % de retrabalho).
 */

public record ItemRetrabalhoResponse(int itemCodigo, String descricao, int osNumero, String clienteNome, LocalDateTime dataFinalizacao,
        List<String> etapasRepetidas) {
}
