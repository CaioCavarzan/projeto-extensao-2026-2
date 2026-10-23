package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import br.fatec.projetoextensao.dtos.ClienteAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.ClientePJRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.FornecedorRequest;
import br.fatec.projetoextensao.dtos.FornecedorResponse;
import br.fatec.projetoextensao.dtos.FuncionarioRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.entities.EnderecoEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;

/**
 * INCLUÍDO (20/10/2026): regras de cadastro de pessoa, endereço, cliente, funcionário e fornecedor
 * (RN-PES-09 a RN-PES-14, RN-END, RN-CLI, RN-PF, RN-PJ, RN-FUN e RN-FOR).
 */
class CadastrosTest extends TesteBase {

    // ------------------------------------------------------------------ pessoa e endereço

    /** RN-PES-09 (nome obrigatório), RN-END-05 (UF) e RN-END-07 (CEP): todos os erros voltam juntos. */
    @Test
    void rnPes09_rnEnd05_rnEnd07_errosDeCampoVoltamJuntos() {
        PessoaRequest invalida = new PessoaRequest("   ", null, null,
                new EnderecoRequest("XX", "Rio Preto", "1501500", "Rua A", null));
        assertThatThrownBy(() -> clienteService.cadastrarPF(new ClientePFRequest(invalida, novoCpf(), null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(e -> assertThat(((RegraNegocioException) e).getMensagens()).containsExactly(
                        "Campo obrigatório: Nome / razão social (até 200 caracteres).",
                        "UF inválida.",
                        "CEP inválido: informe os 8 dígitos."));
    }

    /** RN-END-05, RN-END-07, RN-PES-11 e RN-PES-13: dados gravados sem máscara, UF maiúscula, e-mail minúsculo. */
    @Test
    void rnPes11_rnPes13_rnEnd05_rnEnd07_normalizacao() {
        PessoaRequest p = new PessoaRequest("  Ana Souza  ", " Ana@Fenix.com ", "(17) 99999-0000",
                new EnderecoRequest("sp", "Rio Preto", "15015-000", "Rua A", ""));
        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(p, "123.456.789-09", "", null, null));
        assertThat(c.nome()).isEqualTo("Ana Souza");
        assertThat(c.email()).isEqualTo("ana@fenix.com");
        assertThat(c.telefone()).isEqualTo("17999990000");
        assertThat(c.endereco().uf()).isEqualTo("SP");
        assertThat(c.endereco().cep()).isEqualTo("15015000");
        assertThat(c.endereco().nrImovel()).isNull();
        assertThat(c.nomeSocial()).isNull(); // RN-SIS-06: vazio vira NULL
        assertThat(c.cpf()).isEqualTo("12345678909");
    }

    /** RN-PES-10: e-mail repetido é recusado; várias pessoas sem e-mail são aceitas. */
    @Test
    void rnPes10_emailUnicoQuandoInformado() {
        clienteService.cadastrarPF(new ClientePFRequest(new PessoaRequest("Primeiro", "dup@fenix.com", null, endereco()),
                novoCpf(), null, null, null));
        assertThatThrownBy(() -> clienteService.cadastrarPF(new ClientePFRequest(
                new PessoaRequest("Segundo", "DUP@fenix.com", null, endereco()), novoCpf(), null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("E-mail inválido ou já cadastrado.");
        novoClientePF("Sem e-mail 1");
        novoClientePF("Sem e-mail 2");
    }

    /** RN-PES-13: telefone sem DDD é recusado. */
    @Test
    void rnPes13_telefoneSemDdd() {
        PessoaRequest p = new PessoaRequest("Tel Curto", null, "9999-0000", endereco());
        assertThatThrownBy(() -> clienteService.cadastrarPF(new ClientePFRequest(p, novoCpf(), null, null, null)))
                .hasMessage("Telefone inválido: informe DDD e número.");
    }

    /** RN-END-02: endereço usado por duas pessoas não é alterado no lugar; quem mudou recebe um endereço novo. */
    @Test
    void rnEnd02_enderecoCompartilhadoNaoMudaParaOutraPessoa() {
        ClienteResponse a = novoClientePF("Morador A");
        ClienteResponse b = novoClientePF("Morador B");
        PessoaEntity pb = pessoaRepository.findById(b.codigo()).orElseThrow();
        EnderecoEntity enderecoDeA = enderecoRepository.findById(a.endereco().codigo()).orElseThrow();
        pb.setEndereco(enderecoDeA); // os dois passam a dividir o endereço (como pode vir da carga antiga)
        pessoaRepository.save(pb);

        PessoaRequest novoEndereco = new PessoaRequest("Morador B", null, null,
                new EnderecoRequest("SP", "Mirassol", "15130000", "Rua Nova", "5"));
        ClienteResponse bAlterado = clienteService.atualizar(b.codigo(), new ClienteAtualizacaoRequest(novoEndereco,
                null, null, null, null, null));

        assertThat(bAlterado.endereco().codigo()).isNotEqualTo(a.endereco().codigo());
        assertThat(clienteService.buscar(a.codigo()).endereco().cidade()).isEqualTo("São José do Rio Preto");
    }

    // ------------------------------------------------------------------ cliente

    /** RN-PF-03: CPF repetido entre clientes PF é recusado (409). */
    @Test
    void rnPf03_cpfRepetido() {
        String cpf = novoCpf();
        clienteService.cadastrarPF(new ClientePFRequest(pessoa("Primeiro"), cpf, null, null, null));
        assertThatThrownBy(() -> clienteService.cadastrarPF(new ClientePFRequest(pessoa("Segundo"), cpf, null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("CPF inválido ou já cadastrado.")
                .satisfies(e -> assertThat(((RegraNegocioException) e).getTipo()).isEqualTo(RegraNegocioException.Tipo.CONFLITO));
    }

    /** RN-PF-04: dígito verificador do CPF. */
    @Test
    void rnPf04_cpfComDigitoErrado() {
        assertThatThrownBy(() -> clienteService.cadastrarPF(new ClientePFRequest(pessoa("X"), "111.111.111-11", null, null, null)))
                .hasMessage("CPF inválido.");
    }

    /** RN-PJ-03 e RN-PJ-04: CNPJ alfanumérico gravado sem máscara e em maiúsculas. */
    @Test
    void rnPj03_cnpjAlfanumerico() {
        ClienteResponse c = clienteService.cadastrarPJ(new ClientePJRequest(pessoa("Empresa Alfanumérica"),
                "12.abc.345/01de-35", "Alfa", "isento", null, null));
        assertThat(c.cnpj()).isEqualTo("12ABC34501DE35");
        assertThat(c.ie()).isEqualTo("ISENTO"); // RN-PJ-07
    }

    /** RN-PJ-07: IE só com números ou ISENTO. */
    @Test
    void rnPj07_ieInvalida() {
        assertThatThrownBy(() -> clienteService.cadastrarPJ(new ClientePJRequest(pessoa("Empresa IE"), novoCnpj(),
                null, "12.345", null, null)))
                .hasMessage("IE inválida: informe só números ou ISENTO.");
    }

    /**
     * RN-CLI-06: sem fonte informada, o cliente fica com a fonte 'Não informada'.
     * RN-CLI-08 e RN-CLI-09 (22/10/2026): a prioridade é calculada pelo faturamento; cliente sem OS tem 0.
     */
    @Test
    void rnCli06_fonteNaoInformada() {
        ClienteResponse c = novoClientePF("Sem Fonte");
        assertThat(c.fonte()).isEqualTo(FonteAquisicaoService.NAO_INFORMADA);
        assertThat(c.prioridade()).isEqualTo(0);
    }

    /** RN-CLI-03: nome parecido gera aviso, não erro. */
    @Test
    void rnCli03_avisoDeNomeParecido() {
        novoClientePF("Pintura Rápida Ltda");
        ClienteResponse c = novoClientePF("Pintura Rápida");
        assertThat(c.avisos()).contains("Já existe cliente com nome parecido. Confira se não é o mesmo cliente.");
        assertThat(clienteService.buscarSemelhantes("rápida")).hasSizeGreaterThanOrEqualTo(2);
    }

    /** RN-PF-06: nome social aparece no lugar do nome. */
    @Test
    void rnPf06_nomeSocialNaExibicao() {
        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(pessoa("Nome de Registro"), novoCpf(),
                "Nome Social", null, null));
        assertThat(c.nomeExibicao()).isEqualTo("Nome Social");
    }

    /** RN-CLI-02: cliente com OS não é excluído. */
    @Test
    void rnCli02_clienteComOsNaoEExcluido() {
        ClienteResponse c = novoClientePF("Cliente com OS");
        abrirOs(c.codigo(), itemJateamento("Portão", new BigDecimal("100.00")));
        assertThatThrownBy(() -> clienteService.excluir(c.codigo()))
                .hasMessage("Cliente com ordens de serviço não pode ser excluído.");
    }

    // ------------------------------------------------------------------ funcionário

    /** RN-FUN-04: CPF repetido entre funcionários. */
    @Test
    void rnFun04_cpfRepetido() {
        String cpf = novoCpf();
        novoFuncionario("Primeiro", cpf);
        assertThatThrownBy(() -> novoFuncionario("Segundo", cpf)).hasMessage("CPF inválido ou já cadastrado.");
    }

    /** RN-FUN-01: funcionário sem CPF. */
    @Test
    void rnFun01_funcionarioSemCpf() {
        assertThatThrownBy(() -> novoFuncionario("Sem CPF", null)).hasMessage("Informe o CPF do funcionário.");
    }

    /** RN-FUN-07 (cargo da lista), RN-FUN-08 (salário com 3 casas) e RN-FUN-11 (admissão no futuro). */
    @Test
    void rnFun07_rnFun08_rnFun11_validacoes() {
        FuncionarioRequest r = new FuncionarioRequest(pessoa("Validações"), novoCpf(), "Gerente",
                new BigDecimal("1500.555"), LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> funcionarioService.cadastrar(r))
                .satisfies(e -> assertThat(((RegraNegocioException) e).getMensagens()).containsExactly(
                        "Cargo inválido.", "Salário inválido.", "A data de admissão não pode estar no futuro."));
    }

    /** RN-FUN-07: o cargo é gravado com a grafia da lista. */
    @Test
    void rnFun07_cargoComGrafiaDaLista() {
        FuncionarioResponse f = funcionarioService.cadastrar(new FuncionarioRequest(pessoa("Auxiliar"), novoCpf(),
                "auxiliar de produção", new BigDecimal("1800"), LocalDate.now()));
        assertThat(f.cargo()).isEqualTo("Auxiliar de Produção");
    }

    /** RN-FUN-09: ADMIN vê o salário. */
    @Test
    void rnFun09_adminVeSalario() {
        FuncionarioResponse f = novoFuncionario("Com Salário", novoCpf());
        assertThat(funcionarioService.buscar(f.codigo()).salario()).isEqualByComparingTo("2500.00");
    }

    /** RN-FUN-09: OPERADOR não vê o salário (a listagem vem sem ele). */
    @Test
    @WithMockUser(username = "operador.teste", roles = "OPERADOR")
    void rnFun09_operadorNaoVeSalario() {
        List<FuncionarioResponse> lista = funcionarioService.listar();
        assertThat(lista).isNotEmpty();
        assertThat(lista).allSatisfy(f -> assertThat(f.salario()).isNull());
    }

    /** RN-FUN-02: funcionário com usuário não é excluído; desligar bloqueia o acesso. */
    @Test
    void rnFun02_funcionarioComUsuarioEDesligadoNaoExcluido() {
        assertThatThrownBy(() -> funcionarioService.excluir(admin.getFuncionarioCodigo()))
                .hasMessage("Funcionário com registros não pode ser excluído; bloqueie o acesso.");
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Operador Desligado", cpf);
        usuarioService.ativarConta(new br.fatec.projetoextensao.dtos.AtivarContaRequest(cpf, "desligado" + f.codigo(),
                "senhaForte1", "Nome do pet?", "Rex"));
        FuncionarioResponse desligado = funcionarioService.desligar(f.codigo());
        assertThat(desligado.acessoAtivo()).isFalse();
    }

    // ------------------------------------------------------------------ fornecedor

    /** RN-FOR-01: fornecedor sem CNPJ. */
    @Test
    void rnFor01_fornecedorSemCnpj() {
        assertThatThrownBy(() -> novoFornecedor("Sem CNPJ", "  ")).hasMessage("Informe o CNPJ do fornecedor.");
    }

    /** RN-FOR-09: prazo fora da lista padronizada. */
    @Test
    void rnFor09_prazoForaDaLista() {
        assertThatThrownBy(() -> fornecedorService.cadastrar(new FornecedorRequest(pessoa("Prazo"), novoCnpj(), "45 dias")))
                .hasMessage("Prazo de pagamento inválido: escolha À vista, 30 dias, 30/60/90 dias.");
    }

    /** RN-FOR-07: fornecedor nasce ativo. */
    @Test
    void rnFor07_fornecedorNasceAtivo() {
        assertThat(novoFornecedor("Ativo", novoCnpj()).ativo()).isTrue();
    }

    /** RN-FOR-02 e RN-FOR-03: fornecedor com tinta não é excluído; inativo não recebe tinta nova. */
    @Test
    void rnFor02_rnFor03_fornecedorComTintaEInativo() {
        FornecedorResponse f = novoFornecedor("Fornecedor de Tinta", novoCnpj());
        int cor = novaCor("Azul");
        tintaService.cadastrar(new TintaRequest(f.codigo(), cor, "Azul fosco", null, null, "AZ-1", new BigDecimal("30")));
        assertThatThrownBy(() -> fornecedorService.excluir(f.codigo()))
                .hasMessage("Fornecedor com tintas não pode ser excluído; inative-o.");
        fornecedorService.inativar(f.codigo());
        assertThatThrownBy(() -> tintaService.cadastrar(new TintaRequest(f.codigo(), cor, "Azul brilho", null, null,
                "AZ-2", new BigDecimal("30"))))
                .hasMessage("Fornecedor inativo: escolha outro.");
    }
}
