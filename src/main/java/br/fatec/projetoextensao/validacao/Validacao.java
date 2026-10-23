package br.fatec.projetoextensao.validacao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.fatec.projetoextensao.exceptions.RegraNegocioException;

/**
 * INCLUÍDO (20/10/2026): junta os erros de validação de um cadastro e lança uma única RegraNegocioException com
 * todos eles (RN-SIS-07). Assim o front recebe, de uma vez, todos os campos que precisam ser corrigidos.
 *
 * Uso nos Services:
 * <pre>
 *   Validacao v = new Validacao();
 *   String nome = v.obrigatorio(dto.nome(), "Nome / razão social", 200);   // RN-PES-09
 *   String obs  = v.opcional(dto.observacao(), "Observação", 300);         // RN-CLI-07
 *   v.verificar();                                                         // lança se houver erro
 * </pre>
 * As mensagens seguem o padrão da coluna K da planilha de regras.
 */
public class Validacao {

    private final List<String> erros = new ArrayList<>();

    /**
     * Texto obrigatório (coluna NOT NULL): remove os espaços das pontas e recusa vazio ou acima do limite.
     * Mensagem: "Campo obrigatório: &lt;rótulo&gt; (até &lt;máximo&gt; caracteres)."
     */
    public String obrigatorio(String valor, String rotulo, int maximo) {
        String v = Textos.limpar(valor);
        if (v == null || v.length() > maximo) {
            erros.add("Campo obrigatório: " + rotulo + " (até " + maximo + " caracteres).");
        }
        return v;
    }

    /**
     * Texto opcional (coluna NULL): vazio vira null (RN-SIS-06) e texto acima do limite é recusado.
     * Mensagem: "&lt;rótulo&gt;: no máximo &lt;máximo&gt; caracteres."
     */
    public String opcional(String valor, String rotulo, int maximo) {
        String v = Textos.limpar(valor);
        if (v != null && v.length() > maximo) {
            erros.add(rotulo + ": no máximo " + maximo + " caracteres.");
        }
        return v;
    }

    /**
     * Valor em R$ ou quantidade (RN-SIS-11): no máximo 2 casas decimais e até {@code digitosInteiros} dígitos
     * antes da vírgula (decimal(18,2) = 16; decimal(10,2) = 8). {@code minimo} e {@code incluiMinimo} reproduzem
     * os CHECK do script (ex.: salario &gt;= 0, qtd_total &gt; 0).
     */
    public BigDecimal valor(BigDecimal valor, boolean obrigatorio, BigDecimal minimo, boolean incluiMinimo,
                            int digitosInteiros, String mensagem) {
        if (valor == null) {
            if (obrigatorio) {
                erros.add(mensagem);
            }
            return null;
        }
        int comparacao = valor.compareTo(minimo);
        boolean acimaDoMinimo = incluiMinimo ? comparacao >= 0 : comparacao > 0;
        if (!acimaDoMinimo || !Valores.casasValidas(valor, digitosInteiros)) {
            erros.add(mensagem);
            return valor;
        }
        return Valores.normalizar(valor);
    }

    /** Regra genérica: se a condição não vale, registra a mensagem. */
    public void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            erros.add(mensagem);
        }
    }

    public void erro(String mensagem) {
        erros.add(mensagem);
    }

    public boolean temErros() {
        return !erros.isEmpty();
    }

    /** Lança RegraNegocioException (HTTP 400) com todos os erros encontrados, se houver algum. */
    public void verificar() {
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }
    }
}
