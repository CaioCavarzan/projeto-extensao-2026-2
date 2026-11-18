package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * INCLUÍDO (18/11/2026) [E3-19]: resultado da instalação dos scripts da Etapa 3 ou do restore: o que foi executado,
 * as mensagens do SQL Server e a situação do banco logo depois.
 */
public record OperacaoBancoResponse(
        String operacao,
        LocalDateTime concluidaEm,
        List<String> mensagens,
        SituacaoBancoResponse situacao) {
}
