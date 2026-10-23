package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * INCLUÍDO (20/10/2026). Etapa da rota do item (ItensRotasProcessos). retrabalho = a mesma etapa já apareceu antes na rota (RN-IRP-02).
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
