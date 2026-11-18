package br.fatec.projetoextensao.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INCLUÍDO (20/10/2026): RN-SIS-07. Cada exceção vira o status HTTP certo, com a mensagem da regra, e o erro do
 * banco nunca chega cru. Usa um Controller só de teste (não existe no sistema).
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ControllerDeTeste {
        @GetMapping("/regra") void regra() { throw new RegraNegocioException("Inclua pelo menos um item na OS."); }
        @GetMapping("/conflito") void conflito() { throw RegraNegocioException.conflito("Este login já está em uso."); }
        @GetMapping("/nao-encontrado") void naoEncontrado() { throw new RecursoNaoEncontradoException("Cliente não encontrado."); }
        @GetMapping("/sessao") void sessao() { throw new SessaoInvalidaException(); }
        @GetMapping("/negado") void negado() { throw new AccessDeniedException("negado"); }
        @GetMapping("/cpf-duplicado") void cpfDuplicado() {
            throw new DataIntegrityViolationException("erro", new SQLException(
                    "Violation of UNIQUE KEY constraint 'uq_funcionarios_cpf'. Cannot insert duplicate key in object 'dbo.Funcionarios'."));
        }
        @GetMapping("/fk") void fk() {
            throw new DataIntegrityViolationException("erro", new SQLException(
                    "The DELETE statement conflicted with the REFERENCE constraint \"fk_tintas_cores\"."));
        }
        @GetMapping("/inesperado") void inesperado() { throw new IllegalStateException("detalhe interno"); }

        // INCLUÍDO (18/11/2026) [E3-53]: erros dos objetos da Etapa 3 chegando nas várias formas possíveis.
        /** THROW da procedure, como o JdbcTemplate entrega (UncategorizedSQLException). */
        @GetMapping("/procedure-50003") void procedure50003() {
            throw new UncategorizedSQLException("CallableStatementCallback", "{call dbo.sp_ajustar_estoque_tinta(?, ?, ?)}",
                    new SQLException("Estoque insuficiente para realizar a baixa.", "S0001", 50003));
        }
        /** THROW do trigger no flush do Hibernate (JpaSystemException), com o erro 3609 que o SQL Server manda junto. */
        @GetMapping("/trigger-50110") void trigger50110() {
            SQLException principal = new SQLException(
                    "Inconsistência no item: a cor informada diverge da cor cadastrada na tinta.", "S0001", 50110);
            principal.setNextException(new SQLException("The transaction ended in the trigger. The batch has been aborted.", "S0001", 3609));
            throw new JpaSystemException(new RuntimeException("could not execute statement", principal));
        }
        /** THROW do trigger só no commit (TransactionSystemException). */
        @GetMapping("/commit-50120") void commit50120() {
            throw new TransactionSystemException("Could not commit JPA transaction", new RuntimeException(new SQLException(
                    "O histórico de status do item é imutável: não são permitidos UPDATE ou DELETE.", "S0001", 50120)));
        }
        /** Procedure recusou e o SQL Server mandou junto o 266 (contagem de transações). */
        @GetMapping("/procedure-50010") void procedure50010() {
            SQLException principal = new SQLException("Ordem de serviço não encontrada.", "S0001", 50010);
            principal.setNextException(new SQLException("Transaction count after EXECUTE indicates a mismatching number of BEGIN and COMMIT statements.", "25000", 266));
            throw new UncategorizedSQLException("CallableStatementCallback", "{call dbo.sp_cancelar_ordem_servico(?, ?)}", principal);
        }
        /** Objeto da Etapa 3 não instalado (erro 208 citando a função). */
        @GetMapping("/objeto-ausente") void objetoAusente() {
            throw new UncategorizedSQLException("query", "select ... from dbo.fn_itens_atrasados(?)",
                    new SQLException("Invalid object name 'dbo.fn_itens_atrasados'.", "S0002", 208));
        }
        /** Erro 208 de uma tabela que não é da Etapa 3: continua sendo erro interno. */
        @GetMapping("/outra-tabela") void outraTabela() {
            throw new UncategorizedSQLException("query", "select * from TabelaQueNaoExiste",
                    new SQLException("Invalid object name 'TabelaQueNaoExiste'.", "S0002", 208));
        }
        /** Estouro aritmético que o Spring classifica como violação de integridade. */
        @GetMapping("/estouro") void estouro() {
            throw new DataIntegrityViolationException("erro", new SQLException(
                    "Arithmetic overflow error converting numeric to data type numeric.", "22003", 8115));
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void montar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rnSis07_regraDeNegocio400e409() throws Exception {
        mvc.perform(get("/regra")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Inclua pelo menos um item na OS."));
        mvc.perform(get("/conflito")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagens[0]").value("Este login já está em uso."));
    }

    @Test
    void rnSis07_naoEncontrado404() throws Exception {
        mvc.perform(get("/nao-encontrado")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagens[0]").value("Cliente não encontrado."));
    }

    @Test
    void rnSis01_rnSis02_sessaoEAcesso() throws Exception {
        mvc.perform(get("/sessao")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagens[0]").value("Sessão expirada ou acesso não autorizado. Entre novamente."));
        mvc.perform(get("/negado")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagens[0]").value("Ação permitida só para administradores."));
    }

    @Test
    void rnSis07_erroDoBancoTraduzido() throws Exception {
        mvc.perform(get("/cpf-duplicado")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagens[0]").value("CPF inválido ou já cadastrado."));
        mvc.perform(get("/fk")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagens[0]").value("Não é possível concluir: o registro está em uso por outro cadastro."));
    }

    /**
     * INCLUÍDO (18/11/2026) [E3-53]: o THROW dos scripts da Etapa 3 chega à tela com a mensagem do script e o status
     * certo, venha pelo JdbcTemplate, pelo flush do Hibernate ou pelo commit; 208 de objeto da Etapa 3 vira 503.
     */
    @Test
    void etapa3_errosDoBancoTraduzidos() throws Exception {
        mvc.perform(get("/procedure-50003")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Estoque insuficiente para realizar a baixa."));
        mvc.perform(get("/trigger-50110")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Inconsistência no item: a cor informada diverge da cor cadastrada na tinta."));
        mvc.perform(get("/commit-50120")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagens[0]").value("O histórico de status do item é imutável: não são permitidos UPDATE ou DELETE."));
        mvc.perform(get("/procedure-50010")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagens[0]").value("Ordem de serviço não encontrada."));
        mvc.perform(get("/objeto-ausente")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagens[0]").value(org.hamcrest.Matchers.containsString("dbo.fn_itens_atrasados")));
        mvc.perform(get("/outra-tabela")).andExpect(status().isInternalServerError());
        mvc.perform(get("/estouro")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Valor acima do limite que a coluna do banco aceita."));
    }

    @Test
    void rnSis07_erroInesperadoNaoMostraDetalhe() throws Exception {
        mvc.perform(get("/inesperado")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagens[0]").value(
                        "Não foi possível concluir a operação. Tente de novo; se continuar, avise o administrador."));
    }
}
