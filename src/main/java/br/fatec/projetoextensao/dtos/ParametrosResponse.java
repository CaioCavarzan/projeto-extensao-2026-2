package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * Parâmetros que a tela usa para montar listas e conferir antes de enviar (a regra continua no Service):
 * escala de prioridade, unidades, fotos, cargos,
 * prazos de pagamento, unidade do estoque de tinta e tamanho da senha.
 */

public record ParametrosResponse(int prioridadeMinima, int prioridadeMaxima, List<String> unidades, int maxFotosPorItem, int tamanhoMaximoFotoMb,
        List<String> cargos, List<String> prazosPagamento, String unidadeEstoqueTinta, int senhaMinimo, int senhaMaximo) {
}
