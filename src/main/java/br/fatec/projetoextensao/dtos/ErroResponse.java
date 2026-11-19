package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Corpo das respostas de erro: status HTTP, tipo do erro.
 */

public record ErroResponse(
        int status,
        String erro,
        List<String> mensagens,
        String caminho,
        LocalDateTime dataHora) {
}
