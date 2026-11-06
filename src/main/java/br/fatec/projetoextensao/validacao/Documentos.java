package br.fatec.projetoextensao.validacao;

import java.util.Locale;
import java.util.Set;

/**
 * INCLUÍDO (20/10/2026): CPF, CNPJ, CEP, UF, e-mail e telefone.
 *
 * O banco (CHECK do script) só confere o formato: 11 dígitos no CPF; 14 posições no CNPJ, com as 2 últimas
 * numéricas. Aqui também se confere o dígito verificador (RN-PF-04, RN-FUN-05, RN-PJ-04 e RN-FOR-06).
 */
public final class Documentos {

    /** RN-END-05: as 27 siglas do CHECK ck_enderecos_uf. */
    public static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT",
            "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private Documentos() {
    }

    /** CPF sem máscara: '123.456.789-09' vira '12345678909' (RN-PF-03, RN-FUN-04). */
    public static String normalizarCpf(String cpf) {
        return Textos.somenteDigitos(cpf);
    }

    /**
     * CNPJ sem máscara e em maiúsculas: '12.abc.345/01de-35' vira '12ABC34501DE35' (RN-PJ-03, RN-FOR-05).
     * Só pontos, barra, hífen e espaços são retirados; outras letras e números ficam para a validação.
     */
    public static String normalizarCnpj(String cnpj) {
        String t = Textos.limpar(cnpj);
        return t == null ? null : t.replaceAll("[./\\-\\s]", "").toUpperCase(Locale.ROOT);
    }

    /** RN-PF-04 e RN-FUN-05: 11 dígitos, não todos iguais, e os dois dígitos verificadores (módulo 11). */
    public static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || todosIguais(cpf)) {
            return false;
        }
        int d1 = digitoCpf(cpf, 9);
        int d2 = digitoCpf(cpf, 10);
        return d1 == cpf.charAt(9) - '0' && d2 == cpf.charAt(10) - '0';
    }

    private static int digitoCpf(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /**
     * RN-PJ-04 e RN-FOR-06: CNPJ alfanumérico (IN RFB 2.229/2024), que também aceita o CNPJ só numérico.
     * Formato: 12 posições com letras maiúsculas ou números e 2 dígitos verificadores numéricos.
     * Cada caractere vale o seu código ASCII menos 48 ('0' = 0 ... '9' = 9, 'A' = 17 ... 'Z' = 42).
     * 1º dígito: pesos 5,4,3,2,9,8,7,6,5,4,3,2; 2º dígito: pesos 6,5,4,3,2,9,8,7,6,5,4,3,2.
     * Resto da soma por 11 igual a 0 ou 1 dá dígito 0; senão, o dígito é 11 menos o resto.
     */
    public static boolean cnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}\\d{2}") || todosIguais(cnpj)) {
            return false;
        }
        int d1 = digitoCnpj(cnpj.substring(0, 12), new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = digitoCnpj(cnpj.substring(0, 12) + d1, new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return d1 == cnpj.charAt(12) - '0' && d2 == cnpj.charAt(13) - '0';
    }

    private static int digitoCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (base.charAt(i) - 48) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosIguais(String s) {
        return s.chars().distinct().count() == 1;
    }

    /** RN-END-07: CEP com 8 dígitos, só números (a tela pode mandar com máscara). */
    public static boolean cepValido(String cep) {
        return cep != null && cep.matches("\\d{8}");
    }

    /**
     * RN-PES-10 e RN-PES-11: formato de e-mail mais rigoroso que o CHECK do banco ('%_@_%._%'):
     * usuário, @, domínio com ponto e terminação de pelo menos 2 letras, sem espaços.
     */
    public static boolean emailValido(String email) {
        return email != null && email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    }

    /** RN-PES-13: telefone só com dígitos, com DDD: 10 (fixo) ou 11 (celular) dígitos. */
    public static boolean telefoneValido(String telefone) {
        return telefone != null && telefone.matches("\\d{10,11}");
    }
}
