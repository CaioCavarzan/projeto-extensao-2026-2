package br.fatec.projetoextensao.exceptions;

/**
 * Registro não encontrado pelo código (o GlobalExceptionHandler devolve 404).
 * Ex.: "Cliente não encontrado.", "OS não encontrada."
 */

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
