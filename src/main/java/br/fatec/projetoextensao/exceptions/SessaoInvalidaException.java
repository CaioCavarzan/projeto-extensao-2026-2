package br.fatec.projetoextensao.exceptions;

/**
 * INCLUÍDO (20/10/2026): a operação precisa do usuário logado e ele não foi encontrado (RN-SIS-08: a OS e o
 * cronograma registram quem os criou). O GlobalExceptionHandler devolve 401 com a mensagem da RN-SIS-01.
 */
public class SessaoInvalidaException extends RuntimeException {

    public static final String MENSAGEM = "Sessão expirada ou acesso não autorizado. Entre novamente.";

    public SessaoInvalidaException() {
        super(MENSAGEM);
    }
}
