package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Corpo das respostas de erro (RN-SIS-07): status HTTP, tipo do erro e as mensagens da coluna K da planilha.
 */
public record ErroResponse(
        int status,
        String erro,
        List<String> mensagens,
        String caminho,
        LocalDateTime dataHora) {
}
