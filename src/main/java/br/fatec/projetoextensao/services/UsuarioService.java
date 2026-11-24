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
 * Acesso ao sistema (tabela Usuarios).
 * O login em si (conferir login e senha) é feito pelo Spring Security, na classe
 * security.ProvedorAutenticacaoComLimite: só usuário ativo entra, a mensagem de erro é a mesma para
 * login inexistente e senha errada e há limite de tentativas.
 * Senha e resposta de segurança são gravadas só como hash BCrypt e nunca
 * aparecem nas respostas da API nem no log.
 */

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    public static final int SENHA_MINIMO = 8;
    public static final int SENHA_MAXIMO = 72;

    private final UsuarioRepository usuarioRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;
    private final ControleTentativasLogin controleTentativas;
    private final PessoaService pessoaService;

    // ------------------------------------------------------------------ rotas públicas

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
        String login = Textos.normalizarLogin(dto.login()); 
        v.exigir(login != null && login.length() <= 150, "Campo obrigatório: Login (até 150 caracteres)."); 
        validarSenha(v, dto.senha()); 
        String pergunta = v.obrigatorio(dto.perguntaSeguranca(), "Pergunta de segurança", 150);
        String resposta = Textos.normalizarResposta(dto.respostaSeguranca()); 
        v.exigir(resposta != null, "Informe a pergunta e a resposta de segurança juntas."); 
        v.verificar();

        FuncionarioEntity funcionario = funcionarioRepository.findByCpf(cpf)
                .orElseThrow(() -> new RegraNegocioException("Os dados não conferem com nenhum funcionário.")); 
        if (usuarioRepository.existsById(funcionario.getPessoaCodigo())) {
            throw RegraNegocioException.conflito("Este funcionário já tem acesso ao sistema."); 
        }
        if (usuarioRepository.existsByLoginIgnoreCase(login)) {
            throw RegraNegocioException.conflito("Este login já está em uso."); 
        }

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setFuncionario(funcionario); 
        usuario.setLogin(login);
        usuario.setSenhaHash(passwordEncoder.encode(dto.senha())); 
        usuario.setPerguntaSeguranca(pergunta);
        usuario.setRespostaHash(passwordEncoder.encode(resposta)); 
        usuario.setAdmin(false); 
        usuario.setAtivo(true); 
        usuario = usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=ATIVAR_CONTA funcionario={}", login, funcionario.getPessoaCodigo());
        return paraResponse(usuario);
    }

    @PreAuthorize(Perfis.PUBLICO)
    @Transactional(readOnly = true)
    public String buscarPerguntaSeguranca(String login) {
        return usuarioRepository.findByLoginIgnoreCase(Textos.normalizarLogin(login))
                .filter(UsuarioEntity::isAtivo)
                .map(UsuarioEntity::getPerguntaSeguranca)
                .orElseThrow(() -> new RegraNegocioException("Não foi possível recuperar a senha deste login."));
    }

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
            throw new RegraNegocioException("Muitas tentativas. Tente de novo em alguns minutos."); 
        }
        String resposta = Textos.normalizarResposta(dto.respostaSeguranca());
        UsuarioEntity usuario = usuarioRepository.findByLoginIgnoreCase(login).orElse(null);
        boolean confere = usuario != null && usuario.isAtivo() && usuario.getRespostaHash() != null
                && resposta != null && passwordEncoder.matches(resposta, usuario.getRespostaHash());
        if (!confere) {
            controleTentativas.registrarFalha(chave);
            throw new RegraNegocioException("Resposta incorreta."); 
        }
        controleTentativas.limpar(chave);
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha())); 
        usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=REDEFINIR_SENHA", usuario.getLogin());
    }

    // ------------------------------------------------------------------ usuário logado

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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public UsuarioResponse dadosDoUsuarioLogado() {
        return paraResponse(usuarioLogadoEntidade());
    }

    // ------------------------------------------------------------------ meu perfil

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional(readOnly = true)
    public PerfilResponse meuPerfil() {
        return paraPerfil(usuarioLogadoEntidade());
    }

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

    @PreAuthorize(Perfis.QUALQUER)
    @Transactional
    public void alterarPerguntaSeguranca(AlterarPerguntaRequest dto) {
        UsuarioEntity usuario = usuarioLogadoEntidade();
        Validacao v = new Validacao();
        v.exigir(dto != null && dto.senhaAtual() != null && !dto.senhaAtual().isEmpty(), "Informe a senha atual.");
        String pergunta = v.obrigatorio(dto == null ? null : dto.perguntaSeguranca(), "Pergunta de segurança", 150);
        String resposta = Textos.normalizarResposta(dto == null ? null : dto.respostaSeguranca());
        v.exigir(resposta != null, "Informe a pergunta e a resposta de segurança juntas."); 
        v.verificar();
        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("Senha atual incorreta.");
        }
        usuario.setPerguntaSeguranca(pergunta);
        usuario.setRespostaHash(passwordEncoder.encode(resposta)); 
        usuarioRepository.save(usuario);
        log.info("AUDITORIA login={} acao=ALTERAR_PERGUNTA_SEGURANCA", usuario.getLogin());
    }

    // ------------------------------------------------------------------ administração (só ADMIN)

    @PreAuthorize(Perfis.ADMIN)
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAllByOrderByLogin().stream().map(this::paraResponse).toList();
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public UsuarioResponse alterarPerfil(int funcionarioCodigo, boolean admin) {
        UsuarioEntity usuario = buscarEntidade(funcionarioCodigo);
        if (usuario.isAdmin() == admin) {
            return paraResponse(usuario);
        }
        if (!admin && usuario.isAtivo() && usuarioRepository.countByIsAdminTrueAndAtivoTrue() <= 1) {
            throw new RegraNegocioException("Não é possível remover o último administrador."); 
        }
        usuario.setAdmin(admin);
        usuario = usuarioRepository.save(usuario);
        auditar(admin ? "PROMOVER_ADMIN" : "REBAIXAR_OPERADOR", funcionarioCodigo);
        return paraResponse(usuario);
    }

    @PreAuthorize(Perfis.ADMIN)
    @Transactional
    public UsuarioResponse bloquear(int funcionarioCodigo) {
        UsuarioEntity usuario = buscarEntidade(funcionarioCodigo);
        if (!usuario.isAtivo()) {
            return paraResponse(usuario);
        }
        if (usuario.isAdmin() && usuarioRepository.countByIsAdminTrueAndAtivoTrue() <= 1) {
            throw new RegraNegocioException("Não é possível remover o último administrador."); 
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
            v.erro("Informe a senha."); 
        } else if (senha.length() < SENHA_MINIMO || senha.length() > SENHA_MAXIMO) {
            v.erro("A senha precisa ter de 8 a 72 caracteres."); 
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

    private void auditar(String acao, int funcionarioCodigo) {
        log.info("AUDITORIA login={} acao={} usuario={}", usuarioLogado.login().orElse("-"), acao, funcionarioCodigo);
    }
}
