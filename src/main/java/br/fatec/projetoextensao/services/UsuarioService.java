package br.fatec.projetoextensao.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.AlterarPerguntaRequest;
import br.fatec.projetoextensao.dtos.AlterarSenhaRequest;
import br.fatec.projetoextensao.dtos.AtivarContaRequest;
import br.fatec.projetoextensao.dtos.EnderecoRequest;
import br.fatec.projetoextensao.dtos.PerfilRequest;
import br.fatec.projetoextensao.dtos.PerfilResponse;
import br.fatec.projetoextensao.dtos.PessoaRequest;
import br.fatec.projetoextensao.dtos.RedefinirSenhaRequest;
import br.fatec.projetoextensao.dtos.UsuarioResponse;
import br.fatec.projetoextensao.entities.EnderecoEntity;
import br.fatec.projetoextensao.entities.FuncionarioEntity;
import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.UsuarioEntity;
import br.fatec.projetoextensao.exceptions.RecursoNaoEncontradoException;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.exceptions.SessaoInvalidaException;
import br.fatec.projetoextensao.repositories.FuncionarioRepository;
import br.fatec.projetoextensao.repositories.UsuarioRepository;
import br.fatec.projetoextensao.security.Perfis;
import br.fatec.projetoextensao.validacao.Documentos;
import br.fatec.projetoextensao.validacao.Textos;
import br.fatec.projetoextensao.validacao.Validacao;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (20/10/2026): acesso ao sistema (tabela Usuarios).
 * Regras: RN-USU-01 a RN-USU-19, RN-SIS-02/03 (perfis) e RN-SIS-13 (log de alterações sensíveis).
 *
 * O login em si (conferir login e senha) é feito pelo Spring Security, na classe
 * security.ProvedorAutenticacaoComLimite: só usuário ativo entra (RN-USU-04), a mensagem de erro é a mesma para
 * login inexistente e senha errada (RN-USU-07) e há limite de tentativas (RN-USU-08).
 * Senha e resposta de segurança são gravadas só como hash BCrypt (RN-USU-13, RN-USU-14, RN-USU-17) e nunca
 * aparecem nas respostas da API nem no log.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    /** RN-USU-15: senha de 8 a 72 caracteres (o BCrypt ignora o que passa de 72 bytes). */
    public static final int SENHA_MINIMO = 8;
    public static final int SENHA_MAXIMO = 72;

    private final UsuarioRepository usuarioRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;
    private final ControleTentativasLogin controleTentativas;
    // INCLUÍDO (21/10/2026): o próprio usuário altera o e-mail e o telefone da sua pessoa (meu perfil), com as
    // mesmas validações do cadastro de pessoa.
    private final PessoaService pessoaService;

    // ------------------------------------------------------------------ rotas públicas (RN-SIS-01)

    /**
     * Cadastre-se (RN-USU-02): ativação da conta de um funcionário já cadastrado. Ele se identifica pelo CPF e
     * escolhe login, senha, pergunta e resposta de segurança. O usuário nasce OPERADOR (RN-USU-03) e ativo (RN-USU-19).
     */
    @PreAuthorize(Perfis.PUBLICO)
    @Transactional
    public UsuarioResponse ativarConta(AtivarContaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe o CPF, o login, a senha e a pergunta e a resposta de segurança.");
            v.verificar();
        }
        String cpf = Documentos.normalizarCpf(dto.cpf());
        v.exigir(cpf != null && !cpf.isEmpty(), "Informe o CPF.");
        String login = Textos.normalizarLogin(dto.login()); // RN-USU-12: minúsculas, sem espaços
        v.exigir(login != null && login.length() <= 150, "Campo obrigatório: Login (até 150 caracteres)."); // RN-USU-11
        validarSenha(v, dto.senha()); // RN-USU-13, RN-USU-15
        // RN-USU-02: a pergunta e a resposta são escolhidas na ativação; RN-USU-16: vêm juntas.
        String pergunta = v.obrigatorio(dto.perguntaSeguranca(), "Pergunta de segurança", 150);
        String resposta = Textos.normalizarResposta(dto.respostaSeguranca()); // RN-USU-18
        v.exigir(resposta != null, "Informe a pergunta e a resposta de segurança juntas."); // RN-USU-16
        v.verificar();

        FuncionarioEntity funcionario = funcionarioRepository.findByCpf(cpf)
                .orElseThrow(() -> new RegraNegocioException("Os dados não conferem com nenhum funcionário.")); // RN-USU-02
        if (usuarioRepository.existsById(funcionario.getPessoaCodigo())) {
            throw RegraNegocioException.conflito("Este funcionário já tem acesso ao sistema."); // RN-USU-01, RN-USU-09
        }
        if (usuarioRepository.existsByLoginIgnoreCase(login)) {
            throw RegraNegocioException.conflito("Este login já está em uso."); // RN-USU-11, RN-USU-12
        }

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setFuncionario(funcionario); // RN-USU-09: a chave é o código do funcionário (@MapsId)
        usuario.setLogin(login);
        usuario.setSenhaHash(passwordEncoder.encode(dto.senha())); // RN-USU-14: só o hash BCrypt
        usuario.setPerguntaSeguranca(pergunta);
        usuario.setRespostaHash(passwordEncoder.encode(resposta)); // RN-USU-17, RN-USU-18
        usuario.setAdmin(false); // RN-USU-03 e RN-USU-10: nasce OPERADOR, mesmo que o corpo tente outra coisa
        usuario.setAtivo(true); // RN-USU-19
        usuario = usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=ATIVAR_CONTA funcionario={}", login, funcionario.getPessoaCodigo());
        return paraResponse(usuario);
    }

    /** Lembrete de senha, passo 1: mostra a pergunta de segurança do login (RN-USU-05). */
    @PreAuthorize(Perfis.PUBLICO)
    @Transactional(readOnly = true)
    public String buscarPerguntaSeguranca(String login) {
        return usuarioRepository.findByLoginIgnoreCase(Textos.normalizarLogin(login))
                .filter(UsuarioEntity::isAtivo)
                .map(UsuarioEntity::getPerguntaSeguranca)
                .orElseThrow(() -> new RegraNegocioException("Não foi possível recuperar a senha deste login."));
    }

    /**
     * Lembrete de senha, passo 2 (RN-USU-05): se a resposta confere com resposta_hash, grava a nova senha.
     * A senha antiga nunca é mostrada nem enviada. A resposta é normalizada antes de comparar (RN-USU-18).
     * Respostas erradas seguidas bloqueiam novas tentativas por alguns minutos, como no login (RN-USU-08).
     */
    @PreAuthorize(Perfis.PUBLICO)
    @Transactional
    public void redefinirSenha(RedefinirSenhaRequest dto) {
        Validacao v = new Validacao();
        if (dto == null) {
            v.erro("Informe o login, a resposta de segurança e a nova senha.");
            v.verificar();
        }
        String login = Textos.normalizarLogin(dto.login());
        v.exigir(login != null, "Informe o login.");
        validarSenha(v, dto.novaSenha());
        v.verificar();

        String chave = "redefinir:" + login;
        if (controleTentativas.bloqueado(chave)) {
            throw new RegraNegocioException("Muitas tentativas. Tente de novo em alguns minutos."); // RN-USU-08
        }
        String resposta = Textos.normalizarResposta(dto.respostaSeguranca());
        UsuarioEntity usuario = usuarioRepository.findByLoginIgnoreCase(login).orElse(null);
        boolean confere = usuario != null && usuario.isAtivo() && usuario.getRespostaHash() != null
                && resposta != null && passwordEncoder.matches(resposta, usuario.getRespostaHash());
        if (!confere) {
            controleTentativas.registrarFalha(chave);
            throw new RegraNegocioException("Resposta incorreta."); // RN-USU-05
        }
        controleTentativas.limpar(chave);
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha())); // RN-USU-14
        usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=REDEFINIR_SENHA", usuario.getLogin());
    }

    // ------------------------------------------------------------------ usuário logado

    /** Troca de senha pelo próprio usuário: confere a senha atual (RN-USU-14, RN-USU-15). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public void alterarSenha(AlterarSenhaRequest dto) {
        UsuarioEntity usuario = usuarioLogadoEntidade();
        Validacao v = new Validacao();
        v.exigir(dto != null && dto.senhaAtual() != null, "Informe a senha atual.");
        validarSenha(v, dto == null ? null : dto.novaSenha());
        v.verificar();
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("Senha atual incorreta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=ALTERAR_SENHA", usuario.getLogin());
    }

    /** Dados do usuário logado (para o front saber o nome e o perfil depois do login). */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public UsuarioResponse dadosDoUsuarioLogado() {
        return paraResponse(usuarioLogadoEntidade());
    }

    // ------------------------------------------------------------------ meu perfil

    /**
     * INCLUÍDO (21/10/2026): dados do usuário logado para a tela "Meu perfil". A documentação do projeto (Etapa 2)
     * pede que todo usuário faça a alteração do seu perfil. Não traz senha nem resposta de segurança (RN-USU-14).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public PerfilResponse meuPerfil() {
        return paraPerfil(usuarioLogadoEntidade());
    }

    /**
     * INCLUÍDO (21/10/2026): o próprio usuário altera o e-mail e o telefone da sua pessoa. Valem as regras do cadastro
     * de pessoa (e-mail válido e único, RN-PES-10 e RN-PES-11; telefone com DDD, RN-PES-13), feitas pelo PessoaService.
     * O nome e o endereço continuam no cadastro de funcionários, que é do ADMIN (o mesmo cadastro tem o salário).
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public PerfilResponse atualizarMeuPerfil(PerfilRequest dto) {
        UsuarioEntity usuario = usuarioLogadoEntidade();
        PessoaEntity pessoa = usuario.getFuncionario().getPessoa();
        EnderecoEntity e = pessoa.getEndereco();
        PessoaRequest dados = new PessoaRequest(pessoa.getNome(), dto == null ? null : dto.email(),
                dto == null ? null : dto.telefone(),
                new EnderecoRequest(e.getUf(), e.getCidade(), e.getCep(), e.getLogradouro(), e.getNr_imovel()));
        pessoaService.atualizarPessoa(pessoa, dados);
        log.info("AUDITORIA login={} acao=ALTERAR_MEU_PERFIL", usuario.getLogin());
        return paraPerfil(usuario);
    }

    /**
     * INCLUÍDO (21/10/2026): o próprio usuário troca a pergunta e a resposta de segurança, confirmando com a senha
     * atual. RN-USU-16: pergunta e resposta vêm juntas; RN-USU-18: a resposta é normalizada; RN-USU-17: gravada só
     * como hash BCrypt.
     */
    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public void alterarPerguntaSeguranca(AlterarPerguntaRequest dto) {
        UsuarioEntity usuario = usuarioLogadoEntidade();
        Validacao v = new Validacao();
        v.exigir(dto != null && dto.senhaAtual() != null && !dto.senhaAtual().isEmpty(), "Informe a senha atual.");
        String pergunta = v.obrigatorio(dto == null ? null : dto.perguntaSeguranca(), "Pergunta de segurança", 150);
        String resposta = Textos.normalizarResposta(dto == null ? null : dto.respostaSeguranca());
        v.exigir(resposta != null, "Informe a pergunta e a resposta de segurança juntas."); // RN-USU-16
        v.verificar();
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("Senha atual incorreta.");
        }
        usuario.setPerguntaSeguranca(pergunta);
        usuario.setRespostaHash(passwordEncoder.encode(resposta)); // RN-USU-17
        usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=ALTERAR_PERGUNTA_SEGURANCA", usuario.getLogin());
    }

    // ------------------------------------------------------------------ administração (só ADMIN)

    /** RN-SIS-03: gerenciar usuários é só do ADMIN. RN-USU-14: a resposta não traz os hashes. */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAllByOrderByLogin().stream().map(this::paraResponse).toList();
    }

    /**
     * RN-USU-03: só ADMIN muda o perfil de alguém. RN-USU-06: o último ADMIN ativo não é rebaixado.
     * RN-SIS-13: a mudança fica no log.
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public UsuarioResponse alterarPerfil(int funcionarioCodigo, boolean admin) {
        UsuarioEntity usuario = buscarEntidade(funcionarioCodigo);
        if (usuario.isAdmin() == admin) {
            return paraResponse(usuario);
        }
        if (!admin && usuario.isAtivo() && usuarioRepository.countByIsAdminTrueAndAtivoTrue() <= 1) {
            throw new RegraNegocioException("Não é possível remover o último administrador."); // RN-USU-06
        }
        usuario.setAdmin(admin);
        usuario = usuarioRepository.save(usuario);
        auditar(admin ? "PROMOVER_ADMIN" : "REBAIXAR_OPERADOR", funcionarioCodigo);
        return paraResponse(usuario);
    }

    /**
     * Bloqueia o acesso (ativo = 0): o usuário não entra mais, nem com a senha certa (RN-USU-04).
     * RN-USU-06: o último ADMIN ativo não é bloqueado. Usado também no desligamento do funcionário (RN-FUN-02).
     */
    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public UsuarioResponse bloquear(int funcionarioCodigo) {
        UsuarioEntity usuario = buscarEntidade(funcionarioCodigo);
        if (!usuario.isAtivo()) {
            return paraResponse(usuario);
        }
        if (usuario.isAdmin() && usuarioRepository.countByIsAdminTrueAndAtivoTrue() <= 1) {
            throw new RegraNegocioException("Não é possível remover o último administrador."); // RN-USU-06
        }
        usuario.setAtivo(false);
        usuario = usuarioRepository.save(usuario);
        auditar("BLOQUEAR_ACESSO", funcionarioCodigo);
        return paraResponse(usuario);
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public UsuarioResponse desbloquear(int funcionarioCodigo) {
        UsuarioEntity usuario = buscarEntidade(funcionarioCodigo);
        if (!usuario.isAtivo()) {
            usuario.setAtivo(true);
            usuario = usuarioRepository.save(usuario);
            auditar("DESBLOQUEAR_ACESSO", funcionarioCodigo);
        }
        return paraResponse(usuario);
    }

    // ------------------------------------------------------------------ uso interno

    /**
     * RN-SIS-08: o usuário que registra uma OS ou um cronograma é o usuário logado, lido do contexto de segurança;
     * nunca vem do corpo da requisição.
     */
    UsuarioEntity usuarioLogadoEntidade() {
        return usuarioLogado.login()
                .flatMap(usuarioRepository::findByLoginIgnoreCase)
                .orElseThrow(SessaoInvalidaException::new);
    }

    UsuarioEntity buscarEntidade(int funcionarioCodigo) {
        return usuarioRepository.findById(funcionarioCodigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private void validarSenha(Validacao v, String senha) {
        if (senha == null || senha.isEmpty()) {
            v.erro("Informe a senha."); // RN-USU-13
        } else if (senha.length() < SENHA_MINIMO || senha.length() > SENHA_MAXIMO) {
            v.erro("A senha precisa ter de 8 a 72 caracteres."); // RN-USU-15
        }
    }

    private PerfilResponse paraPerfil(UsuarioEntity u) {
        FuncionarioEntity f = u.getFuncionario();
        PessoaEntity p = f.getPessoa();
        return new PerfilResponse(u.getFuncionarioCodigo(), p.getNome(), u.getLogin(), u.isAdmin(), f.getCargo(),
                p.getEmail(), p.getTelefone(), u.getPerguntaSeguranca());
    }

    private UsuarioResponse paraResponse(UsuarioEntity u) {
        return new UsuarioResponse(u.getFuncionarioCodigo(), u.getFuncionario().getPessoa().getNome(), u.getLogin(),
                u.isAdmin(), u.isAtivo(), u.getPerguntaSeguranca());
    }

    /** RN-SIS-13: quem fez, o quê e em qual registro; nunca senha, resposta ou salário. */
    private void auditar(String acao, int funcionarioCodigo) {
        log.info("AUDITORIA login={} acao={} usuario={}", usuarioLogado.login().orElse("-"), acao, funcionarioCodigo);
    }
}
