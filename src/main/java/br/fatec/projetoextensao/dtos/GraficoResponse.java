package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * INCLUÍDO (21/10/2026). Dados de um gráfico do dashboard. disponivel = false enquanto a consulta do gráfico não existe: a tela mostra
 * o gráfico vazio com a mensagem. tipo: "barra", "linha", "rosca" ou "barra-horizontal".
 */
public record GraficoResponse(String nome, String titulo, String tipo, List<String> rotulos, List<SerieGraficoResponse> series,
        boolean disponivel, String mensagem) {
}
