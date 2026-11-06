package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.fatec.projetoextensao.dtos.AtivarContaRequest;
import br.fatec.projetoextensao.dtos.FuncionarioResponse;
import br.fatec.projetoextensao.dtos.RedefinirSenhaRequest;
import br.fatec.projetoextensao.dtos.UsuarioResponse;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;

/** INCLUÍDO (20/10/2026): regras de acesso ao sistema (RN-USU-01 a RN-USU-19). */
class UsuarioServiceTest extends TesteBase {

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UsuarioResponse ativar(String cpf, String login) {
        return usuarioService.ativarConta(new AtivarContaRequest(cpf, login, "senhaForte1", "Nome do primeiro pet?", "Rex"));
    }

    /** RN-USU-02 e RN-USU-03: cadastre-se pelo CPF do funcionário; o usuário nasce OPERADOR e ativo. */
    @Test
    void rnUsu02_rnUsu03_ativacaoPeloCpf() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Novo Usuário", cpf);
        UsuarioResponse u = ativar("  " + cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9)
                + "-" + cpf.substring(9), "Novo.Usuario");
        assertThat(u.funcionarioCodigo()).isEqualTo(f.codigo());
        assertThat(u.admin()).isFalse();
        assertThat(u.ativo()).isTrue();
        assertThat(u.login()).isEqualTo("novo.usuario"); // RN-USU-12
    }

    /** RN-USU-02: CPF de quem não é funcionário é recusado. */
    @Test
    void rnUsu02_cpfQueNaoEDeFuncionario() {
        String cpf = novoCpf();
        assertThatThrownBy(() -> ativar(cpf, "ninguem"))
                .hasMessage("Os dados não conferem com nenhum funcionário.");
    }

    /** RN-USU-01: ativar a conta do mesmo funcionário duas vezes. */
    @Test
    void rnUsu01_segundaAtivacao() {
        String cpf = novoCpf();
        novoFuncionario("Dupla Ativação", cpf);
        ativar(cpf, "dupla1");
        assertThatThrownBy(() -> ativar(cpf, "dupla2")).hasMessage("Este funcionário já tem acesso ao sistema.");
    }

    /** RN-USU-12: 'Pedro' e depois 'pedro' -> o segundo é recusado. */
    @Test
    void rnUsu12_loginUnicoSemDiferenciarMaiusculas() {
        String cpf1 = novoCpf();
        String cpf2 = novoCpf();
        novoFuncionario("Pedro Um", cpf1);
        novoFuncionario("Pedro Dois", cpf2);
        ativar(cpf1, "Pedro.Teste");
        assertThatThrownBy(() -> ativar(cpf2, "pedro.teste")).hasMessage("Este login já está em uso.");
    }

    /** RN-USU-15 e RN-USU-16: senha curta e pergunta sem resposta. */
    @Test
    void rnUsu15_rnUsu16_senhaCurtaEPerguntaSemResposta() {
        String cpf = novoCpf();
        novoFuncionario("Senha Curta", cpf);
        assertThatThrownBy(() -> usuarioService.ativarConta(new AtivarContaRequest(cpf, "curta", "12345", "Pergunta?", null)))
                .satisfies(e -> assertThat(((RegraNegocioException) e).getMensagens()).containsExactly(
                        "A senha precisa ter de 8 a 72 caracteres.",
                        "Informe a pergunta e a resposta de segurança juntas."));
    }

    /** RN-USU-14 e RN-USU-17: senha e resposta gravadas só como hash; a resposta da API não tem hash. */
    @Test
    void rnUsu14_somenteHash() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Hash", cpf);
        ativar(cpf, "hash.teste");
        UsuarioEntity u = usuarioRepository.findById(f.codigo()).orElseThrow();
        assertThat(u.getSenhaHash()).isNotEqualTo("senhaForte1");
        assertThat(passwordEncoder.matches("senhaForte1", u.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("rex", u.getRespostaHash())).isTrue(); // RN-USU-18: resposta normalizada
        assertThat(Arrays.stream(UsuarioResponse.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("senhaHash", "respostaHash");
    }

    /** RN-USU-05 e RN-USU-18: 'Rex' cadastrado e ' rex ' respondido confere; resposta errada não muda a senha. */
    @Test
    void rnUsu05_rnUsu18_redefinicaoDeSenha() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Esqueceu Senha", cpf);
        ativar(cpf, "esqueceu");
        assertThat(usuarioService.buscarPerguntaSeguranca("ESQUECEU")).isEqualTo("Nome do primeiro pet?");

        assertThatThrownBy(() -> usuarioService.redefinirSenha(new RedefinirSenhaRequest("esqueceu", "Totó", "novaSenha123")))
                .hasMessage("Resposta incorreta.");
        assertThat(passwordEncoder.matches("senhaForte1", usuarioRepository.findById(f.codigo()).orElseThrow().getSenhaHash())).isTrue();

        usuarioService.redefinirSenha(new RedefinirSenhaRequest("esqueceu", " rex ", "novaSenha123"));
        assertThat(passwordEncoder.matches("novaSenha123", usuarioRepository.findById(f.codigo()).orElseThrow().getSenhaHash())).isTrue();
    }

    /** RN-USU-08 (aplicada também à redefinição): respostas erradas seguidas bloqueiam novas tentativas. */
    @Test
    void rnUsu08_muitasRespostasErradas() {
        String cpf = novoCpf();
        novoFuncionario("Tentativas", cpf);
        ativar(cpf, "tentativas.redef");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> usuarioService.redefinirSenha(new RedefinirSenhaRequest("tentativas.redef", "errada", "novaSenha123")))
                    .hasMessage("Resposta incorreta.");
        }
        assertThatThrownBy(() -> usuarioService.redefinirSenha(new RedefinirSenhaRequest("tentativas.redef", "Rex", "novaSenha123")))
                .hasMessage("Muitas tentativas. Tente de novo em alguns minutos.");
    }

    /** RN-USU-06: o último ADMIN ativo não é rebaixado nem bloqueado. */
    @Test
    void rnUsu06_ultimoAdministrador() {
        usuarioRepository.findAll().stream()
                .filter(u -> u.isAdmin() && u.getFuncionarioCodigo() != admin.getFuncionarioCodigo())
                .forEach(u -> { u.setAdmin(false); usuarioRepository.save(u); });
        int codigo = admin.getFuncionarioCodigo();
        assertThatThrownBy(() -> usuarioService.alterarPerfil(codigo, false))
                .hasMessage("Não é possível remover o último administrador.");
        assertThatThrownBy(() -> usuarioService.bloquear(codigo))
                .hasMessage("Não é possível remover o último administrador.");
    }

    /** RN-USU-03: só ADMIN muda perfil; promovido, o usuário passa a ADMIN; com dois ADMIN, um pode ser rebaixado. */
    @Test
    void rnUsu03_alterarPerfil() {
        String cpf = novoCpf();
        FuncionarioResponse f = novoFuncionario("Promovido", cpf);
        ativar(cpf, "promovido");
        assertThat(usuarioService.alterarPerfil(f.codigo(), true).admin()).isTrue();
        assertThat(usuarioService.alterarPerfil(f.codigo(), false).admin()).isFalse();
    }

    /** RN-SIS-08: os dados do usuário logado vêm do login, não do corpo da requisição. */
    @Test
    void rnSis08_usuarioLogado() {
        assertThat(usuarioService.dadosDoUsuarioLogado().login()).isEqualTo(LOGIN_ADMIN);
    }
}
