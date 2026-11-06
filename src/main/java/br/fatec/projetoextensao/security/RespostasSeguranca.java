package br.fatec.projetoextensao.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.fatec.projetoextensao.dtos.ErroResponse;
import br.fatec.projetoextensao.exceptions.SessaoInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * INCLUÍDO (20/10/2026): respostas de erro de acesso no mesmo formato JSON do GlobalExceptionHandler (RN-SIS-07).
 * <ul>
 *   <li>401 sem login ou com sessão inválida: "Sessão expirada ou acesso não autorizado. Entre novamente." (RN-SIS-01);</li>
 *   <li>401 com login e senha errados: "Login ou senha inválidos." (RN-USU-07);</li>
 *   <li>401 com usuário bloqueado: "Acesso bloqueado. Procure o administrador." (RN-USU-04);</li>
 *   <li>401 depois de muitas tentativas: "Muitas tentativas. Tente de novo em alguns minutos." (RN-USU-08);</li>
 *   <li>403 sem o perfil exigido: "Ação permitida só para administradores." (RN-SIS-02 e RN-SIS-03; ALTERADO em
 *       21/10/2026 para o texto da planilha, que antes era "Você não tem permissão para esta ação.").</li>
 * </ul>
 * Não envia o cabeçalho WWW-Authenticate: assim o navegador não abre a janela própria de login nem guarda a senha
 * (quem pede o login é a tela do front).
 */
@Component
public class RespostasSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RespostasSeguranca(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException excecao) throws IOException {
        String mensagem;
        if (excecao instanceof LockedException) {
            mensagem = ProvedorAutenticacaoComLimite.MSG_TENTATIVAS;
        } else if (excecao instanceof DisabledException) {
            mensagem = ProvedorAutenticacaoComLimite.MSG_BLOQUEADO;
        } else if (request.getHeader("Authorization") != null) {
            mensagem = ProvedorAutenticacaoComLimite.MSG_INVALIDO;
        } else {
            mensagem = SessaoInvalidaException.MENSAGEM;
        }
        escrever(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Não autorizado", mensagem);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException excecao) throws IOException {
        // ALTERADO (21/10/2026): mensagem da RN-SIS-03 na planilha (ver Perfis.MSG_SO_ADMIN).
        escrever(request, response, HttpServletResponse.SC_FORBIDDEN, "Acesso negado", Perfis.MSG_SO_ADMIN);
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, int status, String erro,
                          String mensagem) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErroResponse corpo = new ErroResponse(status, erro, List.of(mensagem), request.getRequestURI(),
                LocalDateTime.now());
        objectMapper.writeValue(response.getOutputStream(), corpo);
    }
}
