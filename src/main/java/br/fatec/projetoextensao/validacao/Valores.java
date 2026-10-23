package br.fatec.projetoextensao.validacao;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * INCLUÍDO (20/10/2026): regras de valores em R$ e de quantidades (RN-SIS-11).
 * Sempre BigDecimal, com 2 casas e arredondamento HALF_UP; nunca float ou double (float perde centavos).
 */
public final class Valores {

    public static final int CASAS = 2;

    /** decimal(18,2): até 16 dígitos antes da vírgula (salário, valor_unit, valor_os, valor_total_item). */
    public static final int DIGITOS_DECIMAL_18_2 = 16;

    /** decimal(10,2): até 8 dígitos antes da vírgula (qtd_total, estoque_atual, qtd_planejado, pontuação). */
    public static final int DIGITOS_DECIMAL_10_2 = 8;

    private Valores() {
    }

    /**
     * Confere se o valor cabe na coluna: no máximo 2 casas decimais (RN-FUN-08: "1500,555 → recusado") e no máximo
     * {@code digitosInteiros} dígitos antes da vírgula.
     */
    public static boolean casasValidas(BigDecimal valor, int digitosInteiros) {
        BigDecimal s = valor.stripTrailingZeros();
        if (s.scale() > CASAS) {
            return false;
        }
        int inteiros = s.precision() - s.scale();
        return inteiros <= digitosInteiros;
    }

    /** Valor com 2 casas (RN-SIS-11). */
    public static BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(CASAS, RoundingMode.HALF_UP);
    }

    /** Valor nulo vira zero, com 2 casas. */
    public static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(CASAS) : normalizar(valor);
    }
}
