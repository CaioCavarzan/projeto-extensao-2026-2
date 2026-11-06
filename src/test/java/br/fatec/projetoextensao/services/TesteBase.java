package br.fatec.projetoextensao.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.fatec.projetoextensao.dtos.AbrirOsRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.ClientePJRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.FornecedorRequest;
import br.fatec.projetoextensao.dtos.FornecedorResponse;
import br.fatec.projetoextensao.dtos.FuncionarioRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.dtos.ItemRequest;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.dtos.OsResponse;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.entities.EnderecoEntity;
import br.fatec.projetoextensao.entities.FuncionarioEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.repositories.ClienteRepository;
import br.fatec.projetoextensao.repositories.CorRepository;
import br.fatec.projetoextensao.repositories.CronogramaItemRepository;
import br.fatec.projetoextensao.repositories.CronogramaRepository;
import br.fatec.projetoextensao.repositories.EnderecoRepository;
import br.fatec.projetoextensao.repositories.FonteAquisicaoRepository;
import br.fatec.projetoextensao.repositories.FornecedorRepository;
import br.fatec.projetoextensao.repositories.FotoRepository;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.HistoricoStatusItemRepository;
import br.fatec.projetoextensao.repositories.HistoricoStatusOsRepository;
import br.fatec.projetoextensao.repositories.ItemRepository;
import br.fatec.projetoextensao.repositories.ItemRotaProcessoRepository;
import br.fatec.projetoextensao.repositories.OrdemServicoRepository;
import br.fatec.projetoextensao.repositories.PessoaFisicaRepository;
import br.fatec.projetoextensao.repositories.PessoaJuridicaRepository;
import br.fatec.projetoextensao.repositories.PessoaRepository;
import br.fatec.projetoextensao.repositories.RotaProcessoRepository;
import br.fatec.projetoextensao.repositories.TintaRepository;
import br.fatec.projetoextensao.repositories.UsuarioRepository;

/**
 * INCLUÍDO (20/10/2026): base dos testes das regras de negócio.
 *
 * Cada teste roda no banco H2 de teste (perfil "test"), dentro de uma transação que é desfeita no fim (@Transactional),
 * então um teste não enxerga os dados do outro. O usuário logado é "admin.teste", com perfil ADMIN (@WithMockUser);
 * os testes do perfil OPERADOR trocam isso no próprio método. Os nomes dos testes começam pelo ID da regra da
 * planilha FENIX_REGRAS_DE_NEGOCIO (ex.: rnPes02_... testa a RN-PES-02), para o grupo marcar a regra como Testada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = TesteBase.LOGIN_ADMIN, roles = "ADMIN")
public abstract class TesteBase {

    public static final String LOGIN_ADMIN = "admin.teste";

    /** Gera documentos diferentes em cada teste (CPF e CNPJ têm UNIQUE no banco). */
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1000);

    @Autowired protected PessoaRepository pessoaRepository;
    @Autowired protected EnderecoRepository enderecoRepository;
    @Autowired protected ClienteRepository clienteRepository;
    @Autowired protected PessoaFisicaRepository pessoaFisicaRepository;
    @Autowired protected PessoaJuridicaRepository pessoaJuridicaRepository;
    @Autowired protected FuncionarioRepository funcionarioRepository;
    @Autowired protected FornecedorRepository fornecedorRepository;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected FonteAquisicaoRepository fonteAquisicaoRepository;
    @Autowired protected CorRepository corRepository;
    @Autowired protected RotaProcessoRepository rotaProcessoRepository;
    @Autowired protected TintaRepository tintaRepository;
    @Autowired protected OrdemServicoRepository ordemServicoRepository;
    @Autowired protected ItemRepository itemRepository;
    @Autowired protected ItemRotaProcessoRepository itemRotaProcessoRepository;
    @Autowired protected HistoricoStatusOsRepository historicoStatusOsRepository;
    @Autowired protected HistoricoStatusItemRepository historicoStatusItemRepository;
    @Autowired protected FotoRepository fotoRepository;
    @Autowired protected CronogramaRepository cronogramaRepository;
    @Autowired protected CronogramaItemRepository cronogramaItemRepository;

    @Autowired protected PessoaService pessoaService;
    @Autowired protected ClienteService clienteService;
    @Autowired protected FuncionarioService funcionarioService;
    @Autowired protected FornecedorService fornecedorService;
    @Autowired protected UsuarioService usuarioService;
    @Autowired protected FonteAquisicaoService fonteAquisicaoService;
    @Autowired protected CorService corService;
    @Autowired protected RotaProcessoService rotaProcessoService;
    @Autowired protected StatusService statusService;
    @Autowired protected TintaService tintaService;
    @Autowired protected OrdemServicoService ordemServicoService;
    @Autowired protected ItemService itemService;
    @Autowired protected ItemRotaProcessoService itemRotaProcessoService;
    @Autowired protected CronogramaService cronogramaService;
    @Autowired private PlatformTransactionManager transactionManager;

    protected UsuarioEntity admin;

    /**
     * RN-SIS-08: a OS e o cronograma gravam o usuário logado. Este método garante que o usuário "admin.teste" existe
     * no banco de teste (criado direto pelos repositories, como faria a carga inicial). A criação roda numa transação
     * (TransactionTemplate) para valer também nos testes marcados com @Transactional(NOT_SUPPORTED).
     */
    @BeforeEach
    void garantirUsuarioLogado() {
        admin = new TransactionTemplate(transactionManager).execute(status ->
                usuarioRepository.findByLoginIgnoreCase(LOGIN_ADMIN).orElseGet(() -> {
            EnderecoEntity e = new EnderecoEntity();
            e.setUf("SP");
            e.setCidade("São José do Rio Preto");
            e.setCep("15015000");
            e.setLogradouro("Rua do Teste");
            e = enderecoRepository.save(e);
            PessoaEntity p = new PessoaEntity();
            p.setNome("Administrador de Teste");
            p.setEndereco(e);
            p = pessoaRepository.save(p);
            FuncionarioEntity f = new FuncionarioEntity();
            f.setPessoa(p);
            f.setCpf(novoCpf());
            f.setCargo("Secretário");
            f.setSalario(new BigDecimal("3000.00"));
            f.setDataAdmissao(LocalDate.now().minusYears(1));
            f = funcionarioRepository.save(f);
            UsuarioEntity u = new UsuarioEntity();
            u.setFuncionario(f);
            u.setLogin(LOGIN_ADMIN);
            u.setSenhaHash("hash-de-teste");
            u.setAdmin(true);
            u.setAtivo(true);
            return usuarioRepository.save(u);
        }));
    }

    // ------------------------------------------------------------------ documentos válidos

    /** CPF válido e único no teste (9 dígitos da sequência + os 2 dígitos verificadores). */
    protected static String novoCpf() {
        String base = String.format("%09d", SEQUENCIA.incrementAndGet());
        int d1 = dvCpf(base);
        int d2 = dvCpf(base + d1);
        return base + d1 + d2;
    }

    private static int dvCpf(String base) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0') * (base.length() + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** CNPJ numérico válido e único no teste (8 dígitos da sequência + "0001" + os 2 dígitos verificadores). */
    protected static String novoCnpj() {
        String base = String.format("%08d", SEQUENCIA.incrementAndGet()) + "0001";
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

    // ------------------------------------------------------------------ DTOs de exemplo

    protected EnderecoRequest endereco() {
        return new EnderecoRequest("sp", "São José do Rio Preto", "15015-000", "Rua Bernardino de Campos", "100");
    }

    protected PessoaRequest pessoa(String nome) {
        return new PessoaRequest(nome, null, "(17) 99999-0000", endereco());
    }

    protected ClienteResponse novoClientePF(String nome) {
        return clienteService.cadastrarPF(new ClientePFRequest(pessoa(nome), novoCpf(), null, null, null));
    }

    protected ClienteResponse novoClientePJ(String nome) {
        return clienteService.cadastrarPJ(new ClientePJRequest(pessoa(nome), novoCnpj(), null, null, null, null));
    }

    protected FuncionarioResponse novoFuncionario(String nome, String cpf) {
        return funcionarioService.cadastrar(new FuncionarioRequest(pessoa(nome), cpf, "Pintor",
                new BigDecimal("2500.00"), LocalDate.now().minusMonths(6)));
    }

    protected FornecedorResponse novoFornecedor(String nome, String cnpj) {
        return fornecedorService.cadastrar(new FornecedorRequest(pessoa(nome), cnpj, "30 dias"));
    }

    protected int novaCor(String nome) {
        return corService.cadastrar(new NomeRequest(nome + " " + SEQUENCIA.incrementAndGet())).codigo();
    }

    protected int rota(String nome) {
        return rotaProcessoRepository.findByNomeIgnoreCase(nome).orElseThrow().getCodigo();
    }

    protected TintaResponse novaTinta(int corCodigo, BigDecimal estoque) {
        FornecedorResponse f = novoFornecedor("Fornecedor de Tinta " + SEQUENCIA.incrementAndGet(), novoCnpj());
        TintaResponse t = tintaService.cadastrar(new TintaRequest(f.codigo(), corCodigo, "Tinta pó poliéster",
                null, null, null, new BigDecimal("45.90")));
        if (estoque != null && estoque.signum() > 0) {
            t = tintaService.ajustarEstoque(t.codigo(), new br.fatec.projetoextensao.dtos.AjusteEstoqueRequest(estoque));
        }
        return t;
    }

    /** Item só de Jateamento: fica 'Aguardando execução' assim que é criado (não precisa de cor nem tinta). */
    protected ItemRequest itemJateamento(String descricao, BigDecimal valor) {
        return new ItemRequest(descricao, new BigDecimal("10"), "PEÇA", null, null, null, valor,
                List.of(rota(RotaProcessoService.JATEAMENTO)));
    }

    /** Item com Jateamento e Pintura, na cor informada e com a tinta informada (ou sem tinta, se null). */
    protected ItemRequest itemPintura(String descricao, int corCodigo, Integer tintaCodigo, BigDecimal valor) {
        return new ItemRequest(descricao, new BigDecimal("100"), "CONJUNTO", corCodigo, tintaCodigo, null, valor,
                List.of(rota(RotaProcessoService.JATEAMENTO), rota(RotaProcessoService.PINTURA)));
    }

    protected OsResponse abrirOs(int clienteCodigo, ItemRequest... itens) {
        return ordemServicoService.abrir(new AbrirOsRequest(clienteCodigo, null, List.of(itens)));
    }

    protected static boolean contem(List<ItemListaResponse> lista, String nome) {
        return lista.stream().anyMatch(i -> i.nome().equals(nome));
    }
}
