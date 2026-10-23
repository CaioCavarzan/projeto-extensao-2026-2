package br.fatec.projetoextensao.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** INCLUÍDO (20/10/2026): validações sem banco: CPF, CNPJ alfanumérico, valores e textos. */
class DocumentosTest {

    /** RN-PF-04 e RN-FUN-05: dígitos verificadores do CPF. */
    @Test
    void rnPf04_cpf() {
        assertThat(Documentos.cpfValido("12345678909")).isTrue();
        assertThat(Documentos.cpfValido("52998224725")).isTrue();
        assertThat(Documentos.cpfValido("12345678900")).isFalse();
        assertThat(Documentos.cpfValido("11111111111")).isFalse();
        assertThat(Documentos.cpfValido("00000000000")).isFalse();
        assertThat(Documentos.cpfValido("1234567890")).isFalse();
        assertThat(Documentos.normalizarCpf("123.456.789-09")).isEqualTo("12345678909");
    }

    /** RN-PJ-04 e RN-FOR-06: CNPJ numérico e alfanumérico (IN RFB 2.229/2024). */
    @Test
    void rnPj04_cnpj() {
        assertThat(Documentos.cnpjValido("11222333000181")).isTrue();
        assertThat(Documentos.cnpjValido("12ABC34501DE35")).isTrue();
        assertThat(Documentos.cnpjValido("12ABC34501DE36")).isFalse();
        assertThat(Documentos.cnpjValido("00000000000000")).isFalse();
        assertThat(Documentos.cnpjValido("12ABC34501DEAB")).isFalse();
        assertThat(Documentos.normalizarCnpj("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
    }

    /** RN-SIS-11 e RN-FUN-08: no máximo 2 casas; BigDecimal soma sem perder centavos. */
    @Test
    void rnSis11_valores() {
        assertThat(Valores.casasValidas(new BigDecimal("1500.555"), Valores.DIGITOS_DECIMAL_18_2)).isFalse();
        assertThat(Valores.casasValidas(new BigDecimal("1500.50"), Valores.DIGITOS_DECIMAL_18_2)).isTrue();
        assertThat(Valores.casasValidas(new BigDecimal("123456789"), Valores.DIGITOS_DECIMAL_10_2)).isFalse();
        assertThat(new BigDecimal("0.10").add(new BigDecimal("0.20"))).isEqualByComparingTo("0.30");
    }

    /** RN-SIS-06, RN-USU-12 e RN-USU-18: espaços nas pontas, vazio vira null, login e resposta em minúsculas. */
    @Test
    void rnSis06_textos() {
        assertThat(Textos.limpar("   ")).isNull();
        assertThat(Textos.limpar("  Ana  ")).isEqualTo("Ana");
        assertThat(Textos.normalizarLogin(" Pedro ")).isEqualTo("pedro");
        assertThat(Textos.normalizarResposta("Rex ")).isEqualTo(Textos.normalizarResposta(" rex"));
    }

    /** RN-PES-10/11 (e-mail), RN-PES-13 (telefone com DDD) e RN-END-07 (CEP). */
    @Test
    void rnPes10_rnPes13_rnEnd07_formatos() {
        assertThat(Documentos.emailValido("ana@fenix.com.br")).isTrue();
        assertThat(Documentos.emailValido("ana@fenix")).isFalse();
        assertThat(Documentos.emailValido("ana fenix@x.com")).isFalse();
        assertThat(Documentos.telefoneValido("17999990000")).isTrue();
        assertThat(Documentos.telefoneValido("1733330000")).isTrue();
        assertThat(Documentos.telefoneValido("999990000")).isFalse();
        assertThat(Documentos.cepValido("15015000")).isTrue();
        assertThat(Documentos.cepValido("1501500")).isFalse();
    }
}
