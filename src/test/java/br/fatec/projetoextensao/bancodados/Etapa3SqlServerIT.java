package br.fatec.projetoextensao.bancodados;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.ObjetoBancoResponse;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.exceptions.ErrosBanco;
import br.fatec.projetoextensao.repositories.CatalogoBancoRepository;
import br.fatec.projetoextensao.repositories.ProcedimentosBancoSqlServer;
import br.fatec.projetoextensao.repositories.RotinasBancoRepository;
import br.fatec.projetoextensao.services.ClienteService;
import br.fatec.projetoextensao.services.ItemService;
import br.fatec.projetoextensao.services.OrdemServicoService;
import br.fatec.projetoextensao.services.RotaProcessoService;
import br.fatec.projetoextensao.repositories.RotaProcessoRepository;

/**
 * INCLUÍDO (18/11/2026) [E3-54]: teste de integração com o SQL Server DE VERDADE (o que o H2 dos outros testes não
 * garante): os 13 objetos instalados, os nomes das colunas das functions/views iguais aos que o Java lê, os códigos
 * de erro das procedures e um fluxo de OS passando pelos triggers e pela procedure de cancelamento.
 *
 * Fica DESLIGADO por padrão (não roda no "mvnw test" comum). Para rodar, com o SQL Server ligado e os scripts da
 * Etapa 3 instalados, de preferência numa CÓPIA do banco (o teste grava dados dentro de uma transação que é desfeita
 * no fim, mas é melhor não arriscar o banco de produção):
 * <pre>
 *   mvnw test -Dtest=Etapa3SqlServerIT -Dfenix.it.sqlserver=true
 *   (ou a variável de ambiente FENIX_IT_SQLSERVER=true)
 * </pre>
 * Usa o application.properties normal (spring.datasource.url do SQL Server) e um ADMIN ativo já cadastrado.
 */
@SpringBootTest
@Transactional
@EnabledIf("habilitado")
class Etapa3SqlServerIT {

    @Autowired private CatalogoBancoRepository catalogo;
    @Autowired private RotinasBancoRepository rotinas;
    @Autowired private ProcedimentosBancoSqlServer procedimentos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClienteService clienteService;
    @Autowired private OrdemServicoService ordemServicoService;
    @Autowired private ItemService itemService;
    @Autowired private RotaProcessoRepository rotaProcessoRepository;

    static boolean habilitado() {
        return Boolean.getBoolean("fenix.it.sqlserver") || "true".equalsIgnoreCase(System.getenv("FENIX_IT_SQLSERVER"));
    }

    @Test
    void osTrezeObjetosEstaoInstalados() {
        assertThat(catalogo.ehSqlServer()).isTrue();
        assertThat(catalogo.bancoAtual()).isEqualToIgnoringCase("banco_fenix");
        List<ObjetoBancoResponse> objetos = catalogo.situacaoObjetos();
        assertThat(objetos).hasSize(13).allSatisfy(o -> {
            assertThat(o.instalado()).as(o.nome()).isTrue();
            assertThat(o.tipoConfere()).as(o.nome() + " com o tipo esperado").isTrue();
        });
        assertThat(objetos).filteredOn(o -> o.habilitado() != null).allSatisfy(o -> assertThat(o.habilitado()).as(o.nome()).isTrue());
    }

    /** Cada leitura do Java roda no SQL Server: se uma coluna mudar de nome no script, este teste acusa. */
    @Test
    void functionsEViewsDevolvemAsColunasQueOJavaLe() {
        LocalDate hoje = LocalDate.now();
        assertThat(rotinas.valorOs(-1)).isEqualByComparingTo("0.00");
        rotinas.itensAtrasados(hoje);
        rotinas.prioridadesClientes(0, 100).values().forEach(p -> assertThat(p).isBetween(0, 100));
        assertThat(rotinas.linhaDoTempoOs(-1)).isEmpty();
        rotinas.painelOs();
        rotinas.osPorStatus();
        rotinas.produtividade(hoje.minusDays(30), hoje);
        rotinas.retrabalhoMensal(hoje.minusMonths(12));
        rotinas.cronogramasFuturosComEtapasDaOs(-1, hoje);
    }

    @Test
    void ajusteRecusaQuantidadeZero50001() {
        assertThatThrownBy(() -> procedimentos.ajustarEstoqueTinta(-1, BigDecimal.ZERO))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.AJUSTE_QUANTIDADE_ZERO)).isTrue());
    }

    @Test
    void ajusteRecusaTintaInexistente50002() {
        assertThatThrownBy(() -> procedimentos.ajustarEstoqueTinta(-1, BigDecimal.ONE))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.AJUSTE_TINTA_NAO_ENCONTRADA)).isTrue());
    }

    @Test
    void cancelamentoRecusaOsInexistente50010() {
        assertThatThrownBy(() -> procedimentos.cancelarOrdemServico(-1))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.CANCELAMENTO_OS_NAO_ENCONTRADA)).isTrue());
    }

    @Test
    void replanejamentoRecusaDatas50020() {
        LocalDate d = LocalDate.now().plusDays(10);
        assertThatThrownBy(() -> procedimentos.replanejarCronograma(d, d, 1))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.REPLANEJAMENTO_DATAS_INVALIDAS)).isTrue());
    }

    @Test
    void replanejamentoRecusaUsuario50021() {
        LocalDate d = LocalDate.now().plusDays(10);
        assertThatThrownBy(() -> procedimentos.replanejarCronograma(d, d.plusDays(1), -1))
                .satisfies(e -> assertThat(ErrosBanco.temCodigo(e, ErrosBanco.REPLANEJAMENTO_USUARIO_INVALIDO)).isTrue());
    }

    /** OS aberta, item cancelado e OS cancelada pelos Services: valor_os acompanha pelos triggers e pela procedure. */
    @Test
    void fluxoDaOsPelosTriggersEPelaProcedure() {
        List<String> admins = jdbc.queryForList(
                "select top (1) login from Usuarios where ativo = 1 and is_admin = 1 order by funcionario_codigo", String.class);
        assumeTrue(!admins.isEmpty(), "Cadastre um ADMIN ativo para rodar o fluxo.");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admins.get(0), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            int jateamento = rotaProcessoRepository.findByNomeIgnoreCase(RotaProcessoService.JATEAMENTO).orElseThrow().getCodigo();
            int cliente = clienteService.cadastrarPF(new ClientePFRequest(new PessoaRequest("Cliente IT Etapa 3 " + System.nanoTime(),
                    null, "(17) 99999-0000", new EnderecoRequest("SP", "São José do Rio Preto", "15015-000", "Rua do Teste", "1")),
                    cpfValido(), null, null, null)).codigo();
            OsResponse os = ordemServicoService.abrir(new AbrirOsRequest(cliente, null, List.of(
                    new ItemRequest("Peça IT 1", BigDecimal.ONE, "PEÇA", null, null, null, new BigDecimal("100.00"), List.of(jateamento)),
                    new ItemRequest("Peça IT 2", BigDecimal.ONE, "PEÇA", null, null, null, new BigDecimal("50.00"), List.of(jateamento)))));
            assertThat(os.valorOs()).isEqualByComparingTo("150.00");
            assertThat(rotinas.valorOs(os.numero())).isEqualByComparingTo("150.00");

            itemService.cancelar(os.itens().get(1).codigo());
            assertThat(ordemServicoService.buscar(os.numero()).valorOs()).isEqualByComparingTo("100.00");

            OsResponse cancelada = ordemServicoService.cancelar(os.numero());
            assertThat(cancelada.status()).isEqualTo("Cancelada");
            assertThat(cancelada.valorOs()).isEqualByComparingTo("0.00");
            assertThat(ordemServicoService.auditoria(os.numero())).isNotEmpty();
            assertThat(rotinas.painelOs(os.numero())).hasValueSatisfying(p -> assertThat(p.qtdItensCancelados()).isEqualTo(2));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String cpfValido() {
        String base = String.format("%09d", ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999));
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
}
