package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado da instalação dos scripts da Etapa 3 ou do restore: o que foi executado,
 * as mensagens do SQL Server e a situação do banco logo depois.
 */

public record OperacaoBancoResponse(
        String operacao,
        LocalDateTime concluidaEm,
        List<String> mensagens,
        SituacaoBancoResponse situacao) {
}
