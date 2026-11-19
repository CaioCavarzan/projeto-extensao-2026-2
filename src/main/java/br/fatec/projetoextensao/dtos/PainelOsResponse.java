package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uma linha da view dbo.vw_painel_ordens_servico (GET /api/ordens/painel).
 *
 * Por que existe: as listas de OS (tela Ordens de Serviço, Clientes, Peças e relatório de OS) usavam
 * GET /api/ordens, que monta cada OS com todos os itens, rotas e fotos só para contar itens e achar atrasos. A view
 * já entrega as contagens prontas numa consulta só. Os campos numero, dataEntrada, clienteCodigo, clienteNome,
 * registradaPor, prioridade, valorOs, status e valorIncompleto têm o MESMO nome do OsResponse, para as telas
 * trocarem de endpoint sem mudar o resto do código.
 *
 * Colunas da view -> campos: nr_os -> numero; aberta_por -> registradaPor; status_atual -> status;
 * status_desde -> statusDesde; tipo_cliente ('PF'/'PJ') -> tipoCliente; documento_cliente -> documentoCliente;
 * qtd_itens, qtd_itens_executados, qtd_itens_cancelados, qtd_itens_em_aberto, qtd_itens_atrasados e
 * valor_incompleto.
 */

public record PainelOsResponse(
        int numero,
        LocalDateTime dataEntrada,
        int clienteCodigo,
        String clienteNome,
        String tipoCliente,
        String documentoCliente,
        String registradaPor,
        Integer prioridade,
        BigDecimal valorOs,
        String status,
        LocalDateTime statusDesde,
        int qtdItens,
        int qtdItensExecutados,
        int qtdItensCancelados,
        int qtdItensEmAberto,
        int qtdItensAtrasados,
        boolean valorIncompleto) {
}
