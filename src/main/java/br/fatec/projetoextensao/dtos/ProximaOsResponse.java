package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * O que a tela Nova OS mostra antes de salvar (GET /api/ordens/proxima): o número previsto
 * da OS e a data de entrada pela hora do servidor (a mesma fonte usada ao gravar).
 */

public record ProximaOsResponse(int numero, LocalDateTime dataEntrada) {
}
