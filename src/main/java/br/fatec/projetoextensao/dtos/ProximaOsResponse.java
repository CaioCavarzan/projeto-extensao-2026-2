package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * INCLUÍDO (19/11/2026): o que a tela Nova OS mostra antes de salvar (GET /api/ordens/proxima): o número previsto
 * da OS e a data de entrada pela hora do servidor (a mesma fonte usada ao gravar, RN-OS-12).
 */
public record ProximaOsResponse(int numero, LocalDateTime dataEntrada) {
}
