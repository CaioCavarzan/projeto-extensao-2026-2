package br.fatec.projetoextensao.exceptions;

import br.fatec.projetoextensao.enums.ObjetoBancoEnum;

/**
 * INCLUÍDO (18/11/2026) [E3-03]: um objeto da Etapa 3 (function, view, procedure ou trigger) não está instalado no
 * banco, e por isso a operação não pôde ser feita. Vira HTTP 503 no GlobalExceptionHandler.
 *
 * Por que existe: desde a integração da Etapa 3 o Java depende desses objetos (ex.: o valor da OS é calculado pelos
 * triggers). Se o grupo esquecer de rodar um script, o SQL Server devolve "Invalid object name..." (erro 208, 2812 ou
 * 4121), que sem esta tradução chegaria à tela como "erro interno". Com ela, a mensagem diz qual objeto falta e onde
 * instalar.
 */
public class ObjetoBancoAusenteException extends RuntimeException {

    private final transient ObjetoBancoEnum objeto;

    public ObjetoBancoAusenteException(ObjetoBancoEnum objeto) {
        super("O objeto de banco " + objeto.getNome() + " (" + objeto.getScript() + ") não está instalado no SQL Server. "
                + "O administrador pode instalar em Configurações > Banco de dados ou rodar o script no SSMS.");
        this.objeto = objeto;
    }

    public ObjetoBancoEnum getObjeto() {
        return objeto;
    }
}
