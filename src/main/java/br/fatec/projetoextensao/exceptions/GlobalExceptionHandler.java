package br.fatec.projetoextensao.exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonMappingException;

import br.fatec.projetoextensao.dtos.ErroResponse;
import br.fatec.projetoextensao.security.Perfis;

/**
 * INCLUÍDO (20/10/2026): RN-SIS-07. Toda regra violada vira uma resposta com mensagem clara (coluna K da planilha) e
 * erro do banco nunca chega cru à tela.
 * <ul>
 *   <li>RegraNegocioException: 400 (dado inválido) ou 409 (conflito: duplicidade, registro em uso);</li>
 *   <li>RecursoNaoEncontradoException: 404;</li>
 *   <li>SessaoInvalidaException: 401; AccessDeniedException (perfil sem permissão, @PreAuthorize): 403;</li>
 *   <li>DataIntegrityViolationException (UNIQUE, FK ou CHECK do banco): 409 com a mensagem da regra, achada pelo
 *       nome da constraint do script (ex.: uq_funcionarios_cpf -> "CPF inválido ou já cadastrado.");</li>
 *   <li>foto acima do limite de upload: 413 com a mensagem da RN-FOT-06;</li>
 *   <li>qualquer outro erro: 500 com mensagem genérica (o detalhe vai só para o log do servidor).</li>
 * </ul>
 * Os erros padrão do Spring MVC (rota inexistente, JSON mal formado, método HTTP errado...) são tratados pela
 * classe-mãe ResponseEntityExceptionHandler, com o status correto.
 * ALTERADO (21/10/2026): esses erros padrão passaram a sair também no formato ErroResponse e em português
 * (handleExceptionInternal, no fim da classe); antes saíam no formato padrão do Spring, em inglês, que a tela não lê.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Nome da constraint do script -> mensagem da coluna K da planilha de regras. */
    private static final Map<String, String> MENSAGENS_DO_BANCO = new LinkedHashMap<>();

    static {
        MENSAGENS_DO_BANCO.put("uq_pessoas_fisicas_cpf", "CPF inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("uq_funcionarios_cpf", "CPF inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("ck_pessoas_fisicas_cpf", "CPF inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("ck_funcionarios_cpf", "CPF inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("uq_pessoas_juridicas_cnpj", "CNPJ inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("uq_fornecedores_cnpj", "CNPJ inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("ck_pessoas_juridicas_cnpj", "CNPJ inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("ck_fornecedores_cnpj", "CNPJ inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("ux_pessoas_email", "E-mail inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("ck_pessoas_email", "E-mail inválido ou já cadastrado.");
        MENSAGENS_DO_BANCO.put("uq_usuarios_login", "Este login já está em uso.");
        MENSAGENS_DO_BANCO.put("pk_usuarios", "Este funcionário já tem acesso ao sistema.");
        MENSAGENS_DO_BANCO.put("ck_usuarios_pergunta_resposta", "Informe a pergunta e a resposta de segurança juntas.");
        MENSAGENS_DO_BANCO.put("uq_cores_nome", "Esta cor já está cadastrada.");
        MENSAGENS_DO_BANCO.put("uq_fontes_aquisicao_descricao", "Esta fonte de aquisição já existe.");
        MENSAGENS_DO_BANCO.put("uq_rotasprocessos_nome", "Esta etapa já existe.");
        MENSAGENS_DO_BANCO.put("uq_statusos_status", "Este status já existe.");
        MENSAGENS_DO_BANCO.put("uq_status_itens_nome", "Este status já existe.");
        MENSAGENS_DO_BANCO.put("pk_cronogramas_itens", "Esta etapa já está no cronograma.");
        MENSAGENS_DO_BANCO.put("uq_itens_rotas_processos_item_ordem", "A ordem desta etapa já existe no item.");
        MENSAGENS_DO_BANCO.put("ck_enderecos_uf", "UF inválida.");
        MENSAGENS_DO_BANCO.put("ck_enderecos_cep", "CEP inválido: informe os 8 dígitos.");
        MENSAGENS_DO_BANCO.put("ck_funcionarios_salario", "Salário inválido.");
        MENSAGENS_DO_BANCO.put("ck_tintas_estoque", "Estoque não pode ser negativo.");
        MENSAGENS_DO_BANCO.put("ck_tintas_valor", "Valor unitário inválido.");
        MENSAGENS_DO_BANCO.put("ck_itens_qtd", "Quantidade deve ser maior que zero.");
        MENSAGENS_DO_BANCO.put("ck_itens_valor", "Valor do item inválido.");
        MENSAGENS_DO_BANCO.put("ck_itens_prazo", "O prazo de entrega não pode ser anterior à entrada.");
        MENSAGENS_DO_BANCO.put("ck_itens_tinta_cor", "Para informar a tinta, informe antes a cor pedida.");
        MENSAGENS_DO_BANCO.put("ck_itens_rotas_processos_funcionario", "Informe o operador da etapa.");
        MENSAGENS_DO_BANCO.put("ck_itens_rotas_processos_datas", "A etapa precisa ter sido iniciada.");
        MENSAGENS_DO_BANCO.put("ck_cronogramas_itens_qtd", "Quantidade planejada deve ser maior que zero.");
        MENSAGENS_DO_BANCO.put("ck_cronogramas_itens_posicao", "Posição inválida.");
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException ex, WebRequest request) {
        HttpStatus status = ex.getTipo() == RegraNegocioException.Tipo.CONFLITO ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return resposta(status, "Regra de negócio", ex.getMensagens(), request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex, WebRequest request) {
        return resposta(HttpStatus.NOT_FOUND, "Não encontrado", List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    public ResponseEntity<ErroResponse> sessaoInvalida(SessaoInvalidaException ex, WebRequest request) {
        return resposta(HttpStatus.UNAUTHORIZED, "Não autorizado", List.of(ex.getMessage()), request); // RN-SIS-01
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> naoAutenticado(AuthenticationException ex, WebRequest request) {
        return resposta(HttpStatus.UNAUTHORIZED, "Não autorizado", List.of(SessaoInvalidaException.MENSAGEM), request);
    }

    /** RN-SIS-02: método de Service com @PreAuthorize chamado por usuário sem o perfil exigido. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AccessDeniedException ex, WebRequest request) {
        // ALTERADO (21/10/2026): mensagem da RN-SIS-03 na planilha (era "Você não tem permissão para esta ação.").
        return resposta(HttpStatus.FORBIDDEN, "Acesso negado", List.of(Perfis.MSG_SO_ADMIN), request);
    }

    /** RN-SIS-07: erro do banco traduzido pela constraint; RN-SIS-12: registro em uso (FK) não é excluído. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException ex, WebRequest request) {
        String detalhe = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        String mensagem = null;
        for (Map.Entry<String, String> e : MENSAGENS_DO_BANCO.entrySet()) {
            if (detalhe.contains(e.getKey())) {
                mensagem = e.getValue();
                break;
            }
        }
        if (mensagem == null && (detalhe.contains("foreign key") || detalhe.contains("reference") || detalhe.contains("fk_"))) {
            mensagem = "Não é possível concluir: o registro está em uso por outro cadastro.";
        }
        if (mensagem == null) {
            mensagem = "Não foi possível gravar: os dados não respeitam uma regra do banco.";
        }
        log.warn("Violação de integridade traduzida para '{}': {}", mensagem, ex.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "Conflito", List.of(mensagem), request);
    }

    /** RN-FOT-06: arquivo acima do limite de upload (spring.servlet.multipart.max-file-size). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(corpo(HttpStatus.PAYLOAD_TOO_LARGE,
                "Arquivo grande demais", List.of("Envie uma imagem JPG ou PNG de até 10 MB."), request));
    }

    /** Qualquer outro erro: mensagem genérica para o usuário; o detalhe fica só no log. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> inesperado(Exception ex, WebRequest request) {
        // INCLUÍDO (21/10/2026): o navegador fechou a conexão antes do fim da resposta (ex.: o usuário trocou de tela
        // enquanto uma foto carregava). Não é erro do sistema e não há a quem responder.
        if (clienteDesconectou(ex)) {
            log.debug("Cliente desconectou antes do fim da resposta: {}", ex.getMessage());
            return null;
        }
        log.error("Erro inesperado", ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                List.of("Não foi possível concluir a operação. Tente de novo; se continuar, avise o administrador."), request);
    }

    /**
     * INCLUÍDO (21/10/2026): RN-SIS-07 para os erros padrão do Spring MVC. O status continua o que o Spring escolheu
     * (404 rota inexistente, 405 método não aceito, 400 dado em formato errado...); só o corpo muda para o
     * ErroResponse, com mensagem em português, igual aos demais erros da API.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // Tela inexistente pedida pelo navegador (fora da API): devolve a página 404 do front, e não o JSON.
        if (ex instanceof NoResourceFoundException && pedeTelaForaDaApi(request)) {
            String pagina = pagina404();
            if (pagina != null) {
                // <base>: os links da página (css/app.css, index.html) valem a partir da raiz do sistema, mesmo que o
                // endereço pedido esteja numa "subpasta" que não existe.
                String raiz = ((ServletWebRequest) request).getRequest().getContextPath() + "/";
                pagina = pagina.replace("<head>", "<head>\n  <base href=\"" + raiz + "\">");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                        .body(pagina);
            }
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = statusCode.is5xxServerError() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        }
        if (status.is5xxServerError()) {
            log.error("Erro interno do Spring MVC", ex);
        } else {
            log.debug("Requisição recusada pelo Spring MVC: {}", ex.getMessage());
        }
        ErroResponse corpo = corpo(status, tituloPadrao(status), List.of(mensagemPadrao(ex, status)), request);
        return ResponseEntity.status(status).headers(headers).body(corpo);
    }

    /** INCLUÍDO (21/10/2026): conexão encerrada pelo navegador (ClientAbortException do Tomcat, "Broken pipe"). */
    private static boolean clienteDesconectou(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String nome = t.getClass().getSimpleName();
            if (nome.equals("ClientAbortException") || nome.equals("AsyncRequestNotUsableException")) {
                return true;
            }
        }
        return false;
    }

    /** INCLUÍDO (21/10/2026): a requisição é de uma tela (navegador pedindo HTML) e não da API? */
    private static boolean pedeTelaForaDaApi(WebRequest request) {
        if (!(request instanceof ServletWebRequest swr)) {
            return false;
        }
        String uri = swr.getRequest().getRequestURI();
        String caminho = uri.substring(Math.min(uri.length(), swr.getRequest().getContextPath().length()));
        String aceita = swr.getHeader(HttpHeaders.ACCEPT);
        return !caminho.startsWith("/api/") && aceita != null && aceita.contains(MediaType.TEXT_HTML_VALUE);
    }

    /** INCLUÍDO (21/10/2026): conteúdo de static/404.html, lido uma vez (null se o arquivo não existir). */
    private String pagina404() {
        if (pagina404 == null) {
            try (InputStream in = new ClassPathResource("static/404.html").getInputStream()) {
                pagina404 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Página static/404.html não encontrada: {}", e.getMessage());
                return null;
            }
        }
        return pagina404;
    }

    private volatile String pagina404;

    private static String tituloPadrao(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Não encontrado";
            case METHOD_NOT_ALLOWED -> "Método não permitido";
            case UNSUPPORTED_MEDIA_TYPE, NOT_ACCEPTABLE -> "Formato não aceito";
            case PAYLOAD_TOO_LARGE -> "Arquivo grande demais";
            case SERVICE_UNAVAILABLE -> "Serviço indisponível";
            default -> status.is5xxServerError() ? "Erro interno" : "Requisição inválida";
        };
    }

    private static String mensagemPadrao(Exception ex, HttpStatus status) {
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return "Endereço não encontrado.";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "Esta ação não está disponível neste endereço.";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException || ex instanceof HttpMediaTypeNotAcceptableException) {
            return "Formato de envio não aceito.";
        }
        if (ex instanceof MissingServletRequestParameterException faltando) {
            return "Informe o parâmetro \"" + faltando.getParameterName() + "\".";
        }
        if (ex instanceof MissingServletRequestPartException parte) {
            return "Informe o arquivo \"" + parte.getRequestPartName() + "\".";
        }
        if (ex instanceof TypeMismatchException tipo) {
            String nome = tipo.getPropertyName() == null ? "informado" : "\"" + tipo.getPropertyName() + "\"";
            return "Valor inválido no campo " + nome + ".";
        }
        if (ex instanceof HttpMessageNotReadableException leitura) {
            // Ex.: data em formato errado, texto num campo numérico. O nome do campo ajuda a achar o erro na tela.
            if (leitura.getCause() instanceof JsonMappingException mapeamento && !mapeamento.getPath().isEmpty()) {
                String campo = mapeamento.getPath().get(mapeamento.getPath().size() - 1).getFieldName();
                if (campo != null) {
                    return "Valor inválido no campo \"" + campo + "\".";
                }
            }
            return "Os dados enviados estão incompletos ou em formato inválido.";
        }
        if (status.is5xxServerError()) {
            return "Não foi possível concluir a operação. Tente de novo; se continuar, avise o administrador.";
        }
        return "Não foi possível processar a requisição.";
    }

    private ResponseEntity<ErroResponse> resposta(HttpStatus status, String erro, List<String> mensagens, WebRequest request) {
        return ResponseEntity.status(status).body(corpo(status, erro, mensagens, request));
    }

    private ErroResponse corpo(HttpStatus status, String erro, List<String> mensagens, WebRequest request) {
        String caminho = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
        return new ErroResponse(status.value(), erro, mensagens, caminho, LocalDateTime.now());
    }
}
