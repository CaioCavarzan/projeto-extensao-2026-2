package br.fatec.projetoextensao.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * As 3 stored procedures da Etapa 3 (script procedures.sql), vistas pelo
 * Java como métodos comuns. Cada método devolve o parâmetro OUTPUT da procedure.
 */

public interface ProcedimentosBanco {

    BigDecimal ajustarEstoqueTinta(int tintaCodigo, BigDecimal quantidade);

    int cancelarOrdemServico(int numeroOs);

    int replanejarCronograma(LocalDate dataOrigem, LocalDate dataDestino, int usuarioCodigo);
}
