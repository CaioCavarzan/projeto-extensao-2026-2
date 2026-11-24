package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * Etapa da rota do item (ItensRotasProcessos). retrabalho = a mesma etapa já apareceu antes na rota.
 */

public record EtapaResponse(
        int codigo,
        int itemCodigo,
        int ordem,
        int rotaCodigo,
        String rotaNome,
        LocalDateTime dataInicio,
        LocalDateTime dataConclusao,
        Integer funcionarioCodigo,
        String funcionarioNome,
        boolean retrabalho) {
}
