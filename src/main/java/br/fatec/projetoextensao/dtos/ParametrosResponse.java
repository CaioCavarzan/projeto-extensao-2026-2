package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * INCLUÍDO (21/10/2026). Parâmetros que a tela usa para montar listas e conferir antes de enviar (a regra continua no Service):
 * escala de prioridade (RN-CLI-09), unidades (RN-ITE-17), fotos (RN-FOT-02 e RN-FOT-06), cargos (RN-FUN-07),
 * prazos de pagamento (RN-FOR-09), unidade do estoque de tinta (RN-TIN-15) e tamanho da senha (RN-USU-15).
 */
public record ParametrosResponse(int prioridadeMinima, int prioridadeMaxima, List<String> unidades, int maxFotosPorItem, int tamanhoMaximoFotoMb,
        List<String> cargos, List<String> prazosPagamento, String unidadeEstoqueTinta, int senhaMinimo, int senhaMaximo) {
}
