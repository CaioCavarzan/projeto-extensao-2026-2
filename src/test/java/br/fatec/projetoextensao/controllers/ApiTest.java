package br.fatec.projetoextensao.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.fatec.projetoextensao.dtos.AtivarContaRequest;
import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.FuncionarioRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.services.FuncionarioService;
import br.fatec.projetoextensao.services.UsuarioService;

/**
 * INCLUÍDO (21/10/2026): a API REST usada pelo front, testada de ponta a ponta pelo HTTP (MockMvc) com o Spring
 * Security de verdade: login HTTP Basic, perfil ADMIN, JSON de entrada e de saída, códigos de status e as mensagens
 * da planilha de regras. Cada teste roda numa transação desfeita no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "preparacao.api", roles = "ADMIN")
class ApiTest {

    private static final String SENHA = "senhaForte1";
    private static int sequencia = 7000;

    @Autowired private MockMvc mvc;
    @Autowired private FuncionarioService funcionarioService;
    @Autowired private UsuarioService usuarioService;

    private int adminCodigo;

    // ------------------------------------------------------------------ apoio

    private static String cpf() {
        String base = String.format("%09d", ++sequencia);
        int d1 = dvCpf(base);
        return base + d1 + dvCpf(base + d1);
    }

    private static int dvCpf(String base) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * (base.length() + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static String cnpj() {
        String base = String.format("%08d", ++sequencia) + "0001";
        int d1 = dvCnpj(base, new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = dvCnpj(base + d1, new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return base + d1 + d2;
    }

    private static int dvCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (base.charAt(i) - 48) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static final String ENDERECO = "{\"uf\":\"SP\",\"cidade\":\"São José do Rio Preto\",\"cep\":\"15015-000\",\"logradouro\":\"Rua da API\",\"nrImovel\":\"10\"}";

    /** Funcionário com acesso ativo (cadastre-se) e, se admin, promovido a ADMIN. */
    private int usuario(String login, boolean admin) {
        String c = cpf();
        FuncionarioResponse f = funcionarioService.cadastrar(new FuncionarioRequest(
                new PessoaRequest("Usuário " + login, null, null,
                        new EnderecoRequest("SP", "São José do Rio Preto", "15015000", "Rua A", "1")),
                c, "Pintor", new BigDecimal("2000"), LocalDate.now()));
        usuarioService.ativarConta(new AtivarContaRequest(c, login, SENHA, "Pergunta?", "resposta"));
        if (admin) {
            usuarioService.alterarPerfil(f.codigo(), true);
        }
        return f.codigo();
    }

    @BeforeEach
    void criarAdmin() {
        adminCodigo = usuario("admin.api", true);
    }

    private static String basic(String login) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + SENHA).getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder r) {
        return r.header("Authorization", basic("admin.api"));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r, String corpo) {
        return comoAdmin(r).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static <T> T ler(MvcResult r, String caminho) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), caminho);
    }

    private int idDaEtapa(String nome) throws Exception {
        MvcResult r = mvc.perform(comoAdmin(get("/api/etapas"))).andExpect(status().isOk()).andReturn();
        java.util.List<Integer> ids = ler(r, "$[?(@.nome == '" + nome + "')].codigo");
        return ids.get(0);
    }

    // ------------------------------------------------------------------ testes

    /** CRUD de lista de apoio pelo HTTP: 201, 200 e 204 (RN-SIS-03: só ADMIN). */
    @Test
    void crudDeCoresPelaApi() throws Exception {
        MvcResult criada = mvc.perform(json(post("/api/cores"), "{\"nome\":\"Verde API\"}"))
                .andExpect(status().isCreated()).andReturn();
        int codigo = ler(criada, "$.codigo");
        mvc.perform(json(put("/api/cores/" + codigo), "{\"nome\":\"Verde API 2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Verde API 2"));
        mvc.perform(comoAdmin(get("/api/cores"))).andExpect(jsonPath("$[*].nome", hasItem("Verde API 2")));
        mvc.perform(comoAdmin(delete("/api/cores/" + codigo))).andExpect(status().isNoContent());
    }

    /** Herança: cliente PF com o CPF de um funcionário liga o papel à mesma pessoa (avisa) — RN-PES-02. */
    @Test
    void herancaClienteEFuncionarioPelaApi() throws Exception {
        String c = cpf();
        mvc.perform(json(post("/api/funcionarios"), "{\"pessoa\":{\"nome\":\"Pessoa Dupla\",\"endereco\":" + ENDERECO + "},"
                        + "\"cpf\":\"" + c + "\",\"cargo\":\"Jatista\",\"salario\":2500,\"dataAdmissao\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated());
        MvcResult cliente = mvc.perform(json(post("/api/clientes/pf"), "{\"pessoa\":{\"nome\":\"Pessoa Dupla\",\"endereco\":" + ENDERECO + "},"
                        + "\"cpf\":\"" + c + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos[0]", startsWith("Esta pessoa já está cadastrada como funcionário")))
                .andReturn();
        int codigo = ler(cliente, "$.codigo");
        mvc.perform(comoAdmin(get("/api/clientes/" + codigo)))
                .andExpect(jsonPath("$.papeis", hasItem("Funcionário")))
                .andExpect(jsonPath("$.papeis", hasItem("Cliente PF")));
    }

    /**
     * Fluxo da OS pelo HTTP: abre com item (RN-OS-01), inicia a etapa (OS vai para "Em produção", RN-OS-06), conclui
     * (item "Executada"), conclui a OS (RN-OS-04) e a OS concluída não aceita item novo (RN-OS-07).
     */
    @Test
    void fluxoDaOsPelaApi() throws Exception {
        MvcResult cliente = mvc.perform(json(post("/api/clientes/pj"), "{\"pessoa\":{\"nome\":\"Cliente API Ltda\",\"endereco\":" + ENDERECO + "},"
                        + "\"cnpj\":\"" + cnpj() + "\",\"prioridade\":3}"))
                .andExpect(status().isCreated()).andReturn();
        int clienteCodigo = ler(cliente, "$.codigo");
        int jateamento = idDaEtapa("Jateamento");

        mvc.perform(json(post("/api/ordens"), "{\"clienteCodigo\":" + clienteCodigo + ",\"itens\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens", hasItem("Inclua pelo menos um item na OS.")));

        MvcResult os = mvc.perform(json(post("/api/ordens"), "{\"clienteCodigo\":" + clienteCodigo + ",\"itens\":[{\"descricao\":\"Item API\","
                        + "\"qtdTotal\":2,\"unidade\":\"conjunto\",\"etapas\":[" + jateamento + "]}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Criada"))
                .andExpect(jsonPath("$.prioridade").value(0)) // RN-OS-17: veio do cliente; o 3 enviado no cadastro é ignorado e, sem faturamento, a prioridade calculada é 0
                .andExpect(jsonPath("$.valorIncompleto").value(true)) // RN-OS-15: item sem valor
                .andExpect(jsonPath("$.itens[0].unidade").value("CONJUNTO"))
                .andReturn();
        int numero = ler(os, "$.numero");
        int etapa = ler(os, "$.itens[0].etapas[0].codigo");
        int item = ler(os, "$.itens[0].codigo");

        mvc.perform(json(post("/api/etapas-item/" + etapa + "/iniciar"), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Informe o operador da etapa."));
        mvc.perform(json(post("/api/etapas-item/" + etapa + "/iniciar"), "{\"funcionarioCodigo\":" + adminCodigo + "}"))
                .andExpect(status().isOk());
        mvc.perform(comoAdmin(get("/api/ordens/" + numero))).andExpect(jsonPath("$.status").value("Em produção"));
        mvc.perform(comoAdmin(post("/api/etapas-item/" + etapa + "/concluir"))).andExpect(status().isOk());
        mvc.perform(comoAdmin(get("/api/itens/" + item))).andExpect(jsonPath("$.status").value("Executada"));
        mvc.perform(comoAdmin(post("/api/ordens/" + numero + "/concluir")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Concluída"));
        mvc.perform(json(post("/api/ordens/" + numero + "/itens"), "{\"descricao\":\"Depois\",\"qtdTotal\":1,\"unidade\":\"CONJUNTO\",\"etapas\":[" + jateamento + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("OS encerrada: não aceita alterações."));
        mvc.perform(json(put("/api/itens/" + item + "/entrega"), "{\"nrOse\":\"OSE-API-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entregue").value(true)); // RN-OS-07: exceção da entrega
        mvc.perform(comoAdmin(get("/api/ordens/" + numero + "/historico")))
                .andExpect(jsonPath("$[*].status", hasItem("Concluída")));
    }

    /** Fotos pelo HTTP (multipart): envio, leitura do arquivo e exclusão. */
    @Test
    void fotoPelaApi() throws Exception {
        MvcResult cliente = mvc.perform(json(post("/api/clientes/pf"), "{\"pessoa\":{\"nome\":\"Cliente Foto API\",\"endereco\":" + ENDERECO + "},"
                + "\"cpf\":\"" + cpf() + "\"}")).andExpect(status().isCreated()).andReturn();
        MvcResult os = mvc.perform(json(post("/api/ordens"), "{\"clienteCodigo\":" + ler(cliente, "$.codigo") + ",\"itens\":[{\"descricao\":\"Item foto\","
                + "\"qtdTotal\":1,\"unidade\":\"CONJUNTO\",\"etapas\":[" + idDaEtapa("Jateamento") + "]}]}")).andExpect(status().isCreated()).andReturn();
        int item = ler(os, "$.itens[0].codigo");

        BufferedImage imagem = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", jpg);
        MvcResult foto = mvc.perform(comoAdmin(multipart("/api/itens/" + item + "/fotos")
                        .file(new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", jpg.toByteArray()))))
                .andExpect(status().isCreated()).andReturn();
        int fotoCodigo = ler(foto, "$.codigo");
        MvcResult arquivo = mvc.perform(comoAdmin(get("/api/fotos/" + fotoCodigo + "/arquivo")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn();
        assertThat(arquivo.getResponse().getContentAsByteArray()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
        mvc.perform(comoAdmin(multipart("/api/itens/" + item + "/fotos")
                        .file(new MockMultipartFile("arquivo", "falsa.png", "image/png", "nao e imagem".getBytes()))))
                .andExpect(status().isBadRequest());
        mvc.perform(comoAdmin(delete("/api/fotos/" + fotoCodigo))).andExpect(status().isNoContent());
    }

    /** Dashboard: resumo com dados reais e gráficos programados, mas ainda sem dados ("disponivel": false). */
    @Test
    void dashboardPelaApi() throws Exception {
        mvc.perform(comoAdmin(get("/api/dashboard/resumo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.osPorStatus.Criada").exists())
                .andExpect(jsonPath("$.retrabalhoMes.percentual").exists());
        for (String nome : new String[] {"producao-diaria", "retrabalho-mensal", "os-por-status", "etapas-por-funcionario"}) {
            mvc.perform(comoAdmin(get("/api/dashboard/graficos/" + nome)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disponivel").value(false))
                    .andExpect(jsonPath("$.mensagem", startsWith("Em desenvolvimento")));
        }
        mvc.perform(comoAdmin(get("/api/dashboard/graficos/nao-existe")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagens[0]").value("Gráfico não encontrado."));
    }

    /** Perfil do próprio usuário e parâmetros da tela. */
    @Test
    void perfilEParametrosPelaApi() throws Exception {
        mvc.perform(comoAdmin(get("/api/parametros")))
                .andExpect(jsonPath("$.unidades", hasItem("KG")))
                .andExpect(jsonPath("$.unidades", hasItem("PEÇA")))
                .andExpect(jsonPath("$.unidades.length()").value(5)) // Peça, Conjunto, kg, Metro, Jogo
                .andExpect(jsonPath("$.prioridadeMinima").value(0))
                .andExpect(jsonPath("$.prioridadeMaxima").value(100))
                .andExpect(jsonPath("$.senhaMinimo").value(8))
                .andExpect(jsonPath("$.cargos", hasItem("Jatista")));
        mvc.perform(comoAdmin(get("/api/perfil"))).andExpect(jsonPath("$.login").value("admin.api"));
        mvc.perform(json(put("/api/perfil"), "{\"email\":\"admin.api@exemplo.com\",\"telefone\":\"(17) 98888-7777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("17988887777")); // RN-PES-13: gravado sem máscara
        mvc.perform(json(put("/api/perfil/senha"), "{\"senhaAtual\":\"errada000\",\"novaSenha\":\"outraSenha1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Senha atual incorreta."));
    }

    /** Erros padrão do Spring MVC saem no formato ErroResponse e em português (RN-SIS-07). */
    @Test
    void errosPadraoEmPortugues() throws Exception {
        mvc.perform(json(post("/api/cronogramas"), "{\"dataPlanejada\":\"2026-13-45\",\"etapas\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Valor inválido no campo \"dataPlanejada\"."));
        mvc.perform(comoAdmin(patch("/api/clientes")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.mensagens[0]").value("Esta ação não está disponível neste endereço."));
        mvc.perform(comoAdmin(get("/api/indicadores/retrabalho")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Informe o parâmetro \"inicio\"."));
        mvc.perform(comoAdmin(get("/api/ordens/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagens[0]").value("Valor inválido no campo \"numero\"."));
    }
}
