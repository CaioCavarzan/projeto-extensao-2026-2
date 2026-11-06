package br.fatec.projetoextensao.exceptions;

import java.util.List;

/**
 * INCLUÍDO (20/10/2026): exceção lançada pelos Services quando uma regra de negócio é violada (RN-SIS-07).
 *
 * A mensagem é a da coluna K (Mensagem ao usuário) da planilha FENIX_REGRAS_DE_NEGOCIO. Quando a validação de um
 * cadastro encontra vários erros, todos vão juntos na lista, para o front mostrar de uma vez.
 * O GlobalExceptionHandler devolve 400 para dado inválido e 409 para conflito com o que já está gravado
 * (CPF repetido, login em uso etc.).
 *
 * Estende RuntimeException de propósito: o @Transactional desfaz a transação inteira quando ela é lançada, então
 * nada fica gravado pela metade (RN-SIS-04).
 */
public class RegraNegocioException extends RuntimeException {

    public enum Tipo {
        /** Dado inválido: HTTP 400. */
        INVALIDO,
        /** Conflito com dado já gravado (duplicidade, registro em uso): HTTP 409. */
        CONFLITO
    }

    private final Tipo tipo;
    private final List<String> mensagens;

    public RegraNegocioException(String mensagem) {
        this(Tipo.INVALIDO, List.of(mensagem));
    }

    public RegraNegocioException(List<String> mensagens) {
        this(Tipo.INVALIDO, mensagens);
    }

    public RegraNegocioException(Tipo tipo, List<String> mensagens) {
        super(String.join(" ", mensagens));
        this.tipo = tipo;
        this.mensagens = List.copyOf(mensagens);
    }

    /** Atalho para as regras de duplicidade e de registro em uso (HTTP 409). */
    public static RegraNegocioException conflito(String mensagem) {
        return new RegraNegocioException(Tipo.CONFLITO, List.of(mensagem));
    }

    public Tipo getTipo() {
        return tipo;
    }

    public List<String> getMensagens() {
        return mensagens;
    }
}
