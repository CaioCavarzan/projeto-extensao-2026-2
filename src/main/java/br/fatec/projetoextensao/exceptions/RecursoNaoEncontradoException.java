package br.fatec.projetoextensao.exceptions;

/**
 * INCLUÍDO (20/10/2026): registro não encontrado pelo código (RN-SIS-07: o GlobalExceptionHandler devolve 404).
 * Ex.: "Cliente não encontrado.", "OS não encontrada." (mensagens da coluna K da planilha de regras).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
