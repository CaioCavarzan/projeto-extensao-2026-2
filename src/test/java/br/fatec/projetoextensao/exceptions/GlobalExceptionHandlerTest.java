package br.fatec.projetoextensao.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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

    @Test
    void rnSis07_erroInesperadoNaoMostraDetalhe() throws Exception {
        mvc.perform(get("/inesperado")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagens[0]").value(
                        "Não foi possível concluir a operação. Tente de novo; se continuar, avise o administrador."));
    }
}
