package br.fatec.projetoextensao.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * INCLUÍDO (18/11/2026) [E3-06]: as 3 stored procedures da Etapa 3 (script procedures.sql), vistas pelo
 * Java como métodos comuns. Cada método devolve o parâmetro OUTPUT da procedure.
 *
 * Por que uma interface: no sistema de verdade quem atende é ProcedimentosBancoSqlServer, que chama a procedure com
 * CallableStatement ({call dbo.sp_...}). Os testes automáticos rodam no H2, que não tem stored procedure com
 * parâmetro OUTPUT; lá entra uma imitação (ProcedimentosBancoH2, só em src/test) que faz as mesmas alterações e
 * levanta os mesmos códigos de erro. Os Services dependem só da interface e não sabem qual das duas está atendendo.
 *
 * Os erros das procedures (THROW 50001, 50002, 50003, 50010, 50011, 50012, 50020 e 50021) saem como
 * DataAccessException do Spring; os Services convertem com ErrosBanco.converter para a mensagem do script.
 *
 * IMPORTANTE: as três procedures abrem BEGIN TRAN dentro da transação do Java. Quando dão certo, o COMMIT delas só
 * diminui o contador de transações e quem confirma tudo é o Spring, no fim do método do Service (RN-SIS-04: tudo ou
 * nada). Quando dão erro, o ROLLBACK do CATCH desfaz a transação inteira, inclusive o que o Java já tinha gravado
 * antes no mesmo método; por isso os Services validam as regras ANTES de chamar a procedure.
 */
public interface ProcedimentosBanco {

    /**
     * dbo.sp_ajustar_estoque_tinta(@tinta_codigo, @quantidade, @estoque_resultante OUTPUT): entrada (quantidade
     * positiva) ou baixa (negativa) do estoque, em kg. Devolve o saldo depois do ajuste.
     */
    BigDecimal ajustarEstoqueTinta(int tintaCodigo, BigDecimal quantidade);

    /**
     * dbo.sp_cancelar_ordem_servico(@nr_os, @itens_cancelados OUTPUT): cancela os itens abertos da OS, tira as etapas
     * não concluídas dos cronogramas de hoje em diante, grava 'Cancelada' no histórico da OS e recalcula valor_os.
     * Devolve quantos itens foram cancelados.
     */
    int cancelarOrdemServico(int numeroOs);

    /**
     * dbo.sp_replanejar_cronograma(@data_origem, @data_destino, @usuario_codigo, @etapas_movidas OUTPUT): as etapas
     * não concluídas do cronograma de origem (de itens e OS abertos) passam a constar no cronograma do destino, que é
     * criado se não existir. Devolve quantas etapas entraram no destino.
     */
    int replanejarCronograma(LocalDate dataOrigem, LocalDate dataDestino, int usuarioCodigo);
}
