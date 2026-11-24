package br.fatec.projetoextensao.exceptions;

import java.util.List;

/**
 * Exceção lançada pelos Services quando uma regra de negócio é violada.
 *
 * Quando a validação de um cadastro encontra vários erros, todos vão juntos na lista, para o front mostrar de uma vez.
 * O GlobalExceptionHandler devolve 400 para dado inválido e 409 para conflito com o que já está gravado
 * (CPF repetido, login em uso etc.).
 *
 * Estende RuntimeException de propósito: o @Transactional desfaz a transação inteira quando ela é lançada, então
 * nada fica gravado pela metade.
 */

public class RegraNegocioException extends RuntimeException {

    public enum Tipo {
        INVALIDO,
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
