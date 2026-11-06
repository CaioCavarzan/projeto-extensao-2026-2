package br.fatec.projetoextensao.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.AtivarContaRequest;
import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.FuncionarioRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.services.CorService;
import br.fatec.projetoextensao.services.FuncionarioService;
import br.fatec.projetoextensao.services.UsuarioService;

/**
 * INCLUÍDO (20/10/2026): regras de acesso, testadas pela API com o Spring Security de verdade
 * (RN-SIS-01, RN-SIS-02, RN-SIS-03, RN-USU-04, RN-USU-07 e RN-USU-08).
 *
 * ALTERADO (21/10/2026): com os Controllers criados, as rotas passaram a ser as reais (/api/...), e entraram os testes
 * dos arquivos do front (públicos) e dos cabeçalhos de proteção (Content-Security-Policy).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SegurancaTest {

    @Autowired private MockMvc mvc;
    @Autowired private FuncionarioService funcionarioService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private CorService corService;

    private static int sequencia = 5000;

    private static String cpf() {
        String base = String.format("%09d", ++sequencia);
        int d1 = dv(base);
        int d2 = dv(base + d1);
        return base + d1 + d2;
    }

    private static int dv(String base) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * (base.length() + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** Cria um funcionário e ativa a conta dele (senha "senhaForte1"); devolve o código do funcionário. */
    private int usuario(String login) {
        String cpf = cpf();
        FuncionarioResponse f = funcionarioService.cadastrar(new FuncionarioRequest(
                new PessoaRequest("Usuário " + login, null, null,
                        new EnderecoRequest("SP", "São José do Rio Preto", "15015000", "Rua A", "1")),
                cpf, "Pintor", new BigDecimal("2000"), LocalDate.now()));
        usuarioService.ativarConta(new AtivarContaRequest(cpf, login, "senhaForte1", "Pergunta?", "resposta"));
        return f.codigo();
    }

    private static String basic(String login, String senha) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + senha).getBytes(StandardCharsets.UTF_8));
    }

    /** RN-SIS-01: sem login, a API responde 401 com a mensagem de sessão. */
    @Test
    void rnSis01_semLogin401() throws Exception {
        mvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagens[0]").value("Sessão expirada ou acesso não autorizado. Entre novamente."));
    }

    /**
     * RN-SIS-01: ativação de conta, pergunta de segurança e redefinição de senha não exigem login, e só no método que a
     * tela usa (ALTERADO em 21/10/2026: rotas com /api e presas ao método HTTP).
     */
    @Test
    void rnSis01_rotasPublicas() throws Exception {
        mvc.perform(post("/api/auth/ativar-conta")).andExpect(status().is(not(401)));
        mvc.perform(post("/api/auth/redefinir-senha")).andExpect(status().is(not(401)));
        mvc.perform(get("/api/auth/pergunta-seguranca").param("login", "ninguem")).andExpect(status().is(not(401)));
        mvc.perform(get("/api/auth/redefinir-senha")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    /** Login certo passa pela segurança e chega ao Controller (200). */
    @Test
    @WithMockUser(username = "admin.seguranca", roles = "ADMIN")
    void loginCertoPassa() throws Exception {
        usuario("certo.login");
        mvc.perform(get("/api/clientes").header("Authorization", basic("certo.login", "senhaForte1")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", basic("certo.login", "senhaForte1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("certo.login"))
                .andExpect(jsonPath("$.admin").value(false)); // RN-USU-03: cadastre-se nasce OPERADOR
    }

    /** RN-USU-07: login inexistente e senha errada recebem a mesma mensagem. */
    @Test
    @WithMockUser(username = "admin.seguranca", roles = "ADMIN")
    void rnUsu07_mesmaMensagem() throws Exception {
        usuario("mensagem.unica");
        mvc.perform(get("/api/clientes").header("Authorization", basic("mensagem.unica", "errada123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagens[0]").value("Login ou senha inválidos."));
        mvc.perform(get("/api/clientes").header("Authorization", basic("nao.existe", "errada123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagens[0]").value("Login ou senha inválidos."));
    }

    /** RN-USU-04: usuário bloqueado não entra nem com a senha certa; com a senha errada, a mensagem não revela nada. */
    @Test
    @WithMockUser(username = "admin.seguranca", roles = "ADMIN")
    void rnUsu04_usuarioBloqueado() throws Exception {
        int codigo = usuario("bloqueado.login");
        usuarioService.bloquear(codigo);
        mvc.perform(get("/api/clientes").header("Authorization", basic("bloqueado.login", "senhaForte1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagens[0]").value("Acesso bloqueado. Procure o administrador."));
        mvc.perform(get("/api/clientes").header("Authorization", basic("bloqueado.login", "errada123")))
                .andExpect(jsonPath("$.mensagens[0]").value("Login ou senha inválidos."));
    }

    /** RN-USU-08: depois de 5 senhas erradas seguidas, nem a senha certa entra por alguns minutos. */
    @Test
    @WithMockUser(username = "admin.seguranca", roles = "ADMIN")
    void rnUsu08_limiteDeTentativas() throws Exception {
        usuario("tentativas.login");
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/clientes").header("Authorization", basic("tentativas.login", "errada" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/clientes").header("Authorization", basic("tentativas.login", "senhaForte1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagens[0]").value("Muitas tentativas. Tente de novo em alguns minutos."));
    }

    /** RN-SIS-02 e RN-SIS-03: OPERADOR não altera listas de apoio nem perfis (só ADMIN). */
    @Test
    @WithMockUser(username = "operador.seguranca", roles = "OPERADOR")
    void rnSis03_operadorNaoChamaMetodoDeAdmin() {
        assertThatThrownBy(() -> corService.cadastrar(new NomeRequest("Cor do Operador")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usuarioService.alterarPerfil(1, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** INCLUÍDO (21/10/2026): RN-SIS-02 e RN-SIS-03 pela API: OPERADOR recebe 403 na rota de ADMIN. */
    @Test
    @WithMockUser(username = "admin.seguranca", roles = "ADMIN")
    void rnSis03_operadorRecebe403NaApi() throws Exception {
        usuario("operador.api");
        mvc.perform(get("/api/usuarios").header("Authorization", basic("operador.api", "senhaForte1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagens[0]").value("Ação permitida só para administradores."));
        mvc.perform(post("/api/cores").header("Authorization", basic("operador.api", "senhaForte1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cor do Operador\"}"))
                .andExpect(status().isForbidden());
    }

    /** INCLUÍDO (21/10/2026): os arquivos do front abrem sem login e levam a Content-Security-Policy. */
    @Test
    void arquivosDoFrontSaoPublicosEComCsp() throws Exception {
        mvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CABECALHO_CSP))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/paginas/login.js")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    /** INCLUÍDO (21/10/2026): tela inexistente devolve a página 404 do front; rota inexistente da API, o JSON. */
    @Test
    @WithMockUser(username = "admin.seguranca", roles = "ADMIN")
    void paginaNaoEncontrada() throws Exception {
        usuario("rota.inexistente"); // antes do 1º perform: a requisição limpa o usuário de teste do contexto
        mvc.perform(get("/nao-existe.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Página não encontrada")));
        mvc.perform(get("/api/nao-existe").header("Authorization", basic("rota.inexistente", "senhaForte1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagens[0]").value("Endereço não encontrado."));
    }

    /** RN-SIS-02: sem login, nem os métodos de consulta dos Services são executados. */
    @Test
    void rnSis02_semLoginNaoChamaService() {
        assertThatThrownBy(() -> corService.listar()).isInstanceOf(Exception.class);
    }
}
