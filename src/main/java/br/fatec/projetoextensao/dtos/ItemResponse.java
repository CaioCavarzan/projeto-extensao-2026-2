package br.fatec.projetoextensao.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * INCLUÍDO (20/10/2026). Item devolvido pela API. atrasado = prazo vencido e item não encerrado (RN-ITE-22); entregue = tem nr_ose (RN-ITE-28).
 */
public record ItemResponse(
        int codigo,
        int osNumero,
        String descricao,
        BigDecimal qtdTotal,
        String unidade,
        Integer corCodigo,
        String corNome,
        Integer tintaCodigo,
        String tintaDescricao,
        LocalDateTime dataEntrada,
        LocalDate prazoEntrega,
        boolean atrasado,
        BigDecimal valorTotalItem,
        LocalDateTime dataFinalizacao,
        String nrOse,
        boolean entregue,
        BigDecimal scorePrioridade,
        String status,
        List<EtapaResponse> etapas,
        long quantidadeFotos,
        List<String> avisos) {
}
