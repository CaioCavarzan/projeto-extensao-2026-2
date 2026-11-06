package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.ClientePJRequest;
import br.fatec.projetoextensao.dtos.ClienteResponse;
import br.fatec.projetoextensao.dtos.FornecedorResponse;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.FornecedorEntity;
import br.fatec.projetoextensao.entities.FuncionarioEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.PessoaFisicaEntity;
import br.fatec.projetoextensao.entities.PessoaJuridicaEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;

/**
 * INCLUÍDO (20/10/2026): regras de herança e especialização de Pessoa definidas pelo grupo em 20/10:
 * <ol>
 *   <li>Pessoa tem que ser cliente, funcionário ou fornecedor (RN-PES-01, RN-PES-07);</li>
 *   <li>Pessoa pode ser cliente e funcionário ao mesmo tempo (RN-PES-02, RN-PES-03);</li>
 *   <li>Pessoa pode ser cliente e fornecedor ao mesmo tempo (RN-PES-02, RN-PES-04);</li>
 *   <li>Funcionário não pode ser fornecedor, nem fornecedor pode ser funcionário (RN-PES-05);</li>
 *   <li>Cliente é PF ou PJ, nunca os dois e nunca nenhum (RN-CLI-01, RN-PF-01, RN-PJ-01).</li>
 * </ol>
 */
class EspecializacaoPessoaTest extends TesteBase {

    // ------------------------------------------------------------------ 1. pessoa sempre tem papel

    /** RN-PES-01: falha ao gravar o papel -> a pessoa também não é gravada (sem transação de teste em volta). */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rnPes01_falhaNoPapelNaoDeixaPessoaSolta() {
        long pessoasAntes = pessoaRepository.count();
        long enderecosAntes = enderecoRepository.count();
        ClientePFRequest comFonteInexistente = new ClientePFRequest(pessoa("Cliente Sem Fonte"), novoCpf(), null,
                999999, null);
        assertThatThrownBy(() -> clienteService.cadastrarPF(comFonteInexistente))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Fonte de aquisição não encontrada.");
        assertThat(pessoaRepository.count()).isEqualTo(pessoasAntes);
        assertThat(enderecosAntes).isEqualTo(enderecoRepository.count());
    }

    /** RN-PES-07 e RN-END-01: excluir o único papel exclui a pessoa e o endereço que ficou sem dono. */
    @Test
    void rnPes07_excluirUltimoPapelExcluiPessoaEEndereco() {
        ClienteResponse c = novoClientePF("Cliente Para Excluir");
        int enderecoCodigo = c.endereco().codigo();
        clienteService.excluir(c.codigo());
        assertThat(pessoaRepository.findById(c.codigo())).isEmpty();
        assertThat(enderecoRepository.findById(enderecoCodigo)).isEmpty();
    }

    /** RN-PES-07: excluir um dos papéis mantém a pessoa com o outro papel. */
    @Test
    void rnPes07_excluirUmPapelMantemPessoaComOutroPapel() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Funcionário e Cliente", cpf);
        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(null, cpf, null, null, null));
        clienteService.excluir(c.codigo());
        assertThat(pessoaRepository.findById(f.codigo())).isPresent();
        assertThat(funcionarioRepository.findById(f.codigo())).isPresent();
        assertThat(clienteRepository.findById(f.codigo())).isEmpty();
    }

    // ------------------------------------------------------------------ 2. cliente e funcionário

    /** RN-PES-02 e RN-PES-03: o funcionário que vira cliente é a mesma pessoa, cliente PF com o mesmo CPF. */
    @Test
    void rnPes02_funcionarioViraClientePFNaMesmaPessoa() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Maria Funcionária", cpf);
        long pessoasAntes = pessoaRepository.count();

        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(null, cpf, null, null, null));

        assertThat(c.codigo()).isEqualTo(f.codigo());
        assertThat(c.tipo()).isEqualTo("PF");
        assertThat(c.cpf()).isEqualTo(cpf);
        assertThat(c.nome()).isEqualTo("Maria Funcionária");
        assertThat(c.papeis()).containsExactlyInAnyOrder(PessoaService.PAPEL_CLIENTE_PF, PessoaService.PAPEL_FUNCIONARIO);
        assertThat(c.avisos()).contains("Esta pessoa já está cadastrada como funcionário; o novo papel foi ligado a ela.");
        assertThat(pessoaRepository.count()).isEqualTo(pessoasAntes);
    }

    /** RN-PES-02: o cliente PF que vira funcionário também reaproveita a pessoa. */
    @Test
    void rnPes02_clientePFViraFuncionarioNaMesmaPessoa() {
        String cpf = novoCpf();
        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(pessoa("João Cliente"), cpf, null, null, null));
        FuncionarioResponse f = novoFuncionario("Nome Ignorado", cpf);
        assertThat(f.codigo()).isEqualTo(c.codigo());
        assertThat(f.nome()).isEqualTo("João Cliente");
        assertThat(f.papeis()).containsExactlyInAnyOrder(PessoaService.PAPEL_CLIENTE_PF, PessoaService.PAPEL_FUNCIONARIO);
    }

    /** RN-PES-03: funcionário nunca vira cliente PJ: um CNPJ novo é outra pessoa (a empresa dele). */
    @Test
    void rnPes03_funcionarioNaoViraClientePJ() {
        FuncionarioResponse f = novoFuncionario("Carlos Funcionário", novoCpf());
        ClienteResponse empresa = novoClientePJ("Empresa do Carlos Ltda");
        assertThat(empresa.codigo()).isNotEqualTo(f.codigo());
        assertThatThrownBy(() -> pessoaService.corrigirCnpj(f.codigo(), novoCnpj()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Esta pessoa não tem CNPJ: não é fornecedor nem cliente pessoa jurídica.");
    }

    // ------------------------------------------------------------------ 3. cliente e fornecedor

    /** RN-PES-02 e RN-PES-04: o fornecedor que vira cliente é a mesma pessoa, cliente PJ com o mesmo CNPJ. */
    @Test
    void rnPes02_fornecedorViraClientePJNaMesmaPessoa() {
        String cnpj = novoCnpj();
        FornecedorResponse f = novoFornecedor("Tintas Brasil Ltda", cnpj);
        ClienteResponse c = clienteService.cadastrarPJ(new ClientePJRequest(null, cnpj, null, null, null, null));
        assertThat(c.codigo()).isEqualTo(f.codigo());
        assertThat(c.tipo()).isEqualTo("PJ");
        assertThat(c.papeis()).containsExactlyInAnyOrder(PessoaService.PAPEL_CLIENTE_PJ, PessoaService.PAPEL_FORNECEDOR);
        assertThat(c.avisos()).contains("Esta pessoa já está cadastrada como fornecedor; o novo papel foi ligado a ela.");
    }

    /** RN-PES-02: o cliente PJ que vira fornecedor também reaproveita a pessoa. */
    @Test
    void rnPes02_clientePJViraFornecedorNaMesmaPessoa() {
        String cnpj = novoCnpj();
        ClienteResponse c = clienteService.cadastrarPJ(new ClientePJRequest(pessoa("Metalúrgica Cliente"), cnpj,
                null, null, null, null));
        FornecedorResponse f = novoFornecedor("Nome Ignorado", cnpj);
        assertThat(f.codigo()).isEqualTo(c.codigo());
        assertThat(f.papeis()).containsExactlyInAnyOrder(PessoaService.PAPEL_CLIENTE_PJ, PessoaService.PAPEL_FORNECEDOR);
    }

    // ------------------------------------------------------------------ 4. funcionário x fornecedor

    /**
     * RN-PES-05: uma pessoa não é funcionário e fornecedor. Como o Service liga papéis só pelo CPF ou CNPJ, o
     * conflito só aparece com dado antigo inconsistente; o teste monta esse dado direto pelos repositories.
     */
    @Test
    void rnPes05_pessoaFornecedoraNaoViraFuncionario() {
        String cpf = novoCpf();
        ClienteResponse c = clienteService.cadastrarPF(new ClientePFRequest(pessoa("Pessoa Inconsistente"), cpf,
                null, null, null));
        PessoaEntity p = pessoaRepository.findById(c.codigo()).orElseThrow();
        FornecedorEntity fornecedor = new FornecedorEntity();
        fornecedor.setPessoa(p);
        fornecedor.setCnpj(novoCnpj());
        fornecedor.setPrazoPagamento("30 dias");
        fornecedorRepository.save(fornecedor);

        assertThatThrownBy(() -> novoFuncionario("Pessoa Inconsistente", cpf))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Uma pessoa não pode ser funcionário e fornecedor; cadastre a empresa como outra pessoa.");
    }

    /** RN-PES-05: o contrário: pessoa que é funcionário não vira fornecedor. */
    @Test
    void rnPes05_pessoaFuncionariaNaoViraFornecedor() {
        String cnpj = novoCnpj();
        ClienteResponse c = clienteService.cadastrarPJ(new ClientePJRequest(pessoa("Outra Inconsistente"), cnpj,
                null, null, null, null));
        PessoaEntity p = pessoaRepository.findById(c.codigo()).orElseThrow();
        FuncionarioEntity funcionario = new FuncionarioEntity();
        funcionario.setPessoa(p);
        funcionario.setCpf(novoCpf());
        funcionario.setCargo("Pintor");
        funcionario.setSalario(new BigDecimal("1800.00"));
        funcionario.setDataAdmissao(LocalDate.now());
        funcionarioRepository.save(funcionario);

        assertThatThrownBy(() -> novoFornecedor("Outra Inconsistente", cnpj))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Uma pessoa não pode ser funcionário e fornecedor; cadastre a empresa como outra pessoa.");
    }

    // ------------------------------------------------------------------ 5. cliente é PF ou PJ

    /** RN-CLI-01: não existe cliente "genérico": ClienteEntity é abstrata (só PF ou PJ são gravados). */
    @Test
    void rnCli01_clienteSempreEPFouPJ() {
        assertThat(Modifier.isAbstract(ClienteEntity.class.getModifiers())).isTrue();
        assertThat(PessoaFisicaEntity.class.getSuperclass()).isEqualTo(ClienteEntity.class);
        assertThat(PessoaJuridicaEntity.class.getSuperclass()).isEqualTo(ClienteEntity.class);
    }

    /** RN-PJ-01: a mesma pessoa não tem registro de cliente PF e de cliente PJ. */
    @Test
    void rnPj01_clientePFNaoGanhaRegistroPJ() {
        String cpf = novoCpf();
        ClienteResponse pf = clienteService.cadastrarPF(new ClientePFRequest(pessoa("Cliente PF"), cpf, null, null,
                null));
        // dado inconsistente: a mesma pessoa também aparece como fornecedor (com CNPJ)
        String cnpj = novoCnpj();
        FornecedorEntity f = new FornecedorEntity();
        f.setPessoa(pessoaRepository.findById(pf.codigo()).orElseThrow());
        f.setCnpj(cnpj);
        f.setPrazoPagamento("À vista");
        fornecedorRepository.save(f);

        assertThatThrownBy(() -> clienteService.cadastrarPJ(new ClientePJRequest(null, cnpj, null, null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Este cliente já está cadastrado como pessoa física.");
    }

    // ------------------------------------------------------------------ documento único por pessoa

    /** RN-PES-06: o mesmo CPF não pertence a duas pessoas (Funcionarios e PessoasFisicas juntas). */
    @Test
    void rnPes06_cpfDeOutraPessoaNaCorrecao() {
        String cpfDoFuncionario = novoCpf();
        novoFuncionario("Funcionário A", cpfDoFuncionario);
        ClienteResponse b = novoClientePF("Cliente B");
        assertThatThrownBy(() -> pessoaService.corrigirCpf(b.codigo(), cpfDoFuncionario))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("CPF já cadastrado para outra pessoa.");
    }

    /** RN-PES-03 e RN-PES-06: corrigir o CPF corrige o do funcionário e o do cliente PF da mesma pessoa. */
    @Test
    void rnPes03_correcaoDeCpfMantemOsDoisPapeisIguais() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Ana Dois Papéis", cpf);
        clienteService.cadastrarPF(new ClientePFRequest(null, cpf, null, null, null));
        String novo = novoCpf();
        pessoaService.corrigirCpf(f.codigo(), novo);
        assertThat(funcionarioRepository.findById(f.codigo()).orElseThrow().getCpf()).isEqualTo(novo);
        assertThat(pessoaFisicaRepository.findById(f.codigo()).orElseThrow().getCpf()).isEqualTo(novo);
    }
}
