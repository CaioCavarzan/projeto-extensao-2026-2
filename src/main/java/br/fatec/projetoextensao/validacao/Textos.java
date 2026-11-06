package br.fatec.projetoextensao.validacao;

import java.util.Locale;

/**
 * INCLUÍDO (20/10/2026): normalização de textos antes de validar e gravar (RN-SIS-06).
 * Regra: remover os espaços das pontas; texto que fica vazio vira NULL em coluna opcional e é recusado em coluna
 * obrigatória (quem recusa é a classe Validacao).
 */
public final class Textos {

    private Textos() {
    }

    /** Remove os espaços das pontas; devolve null quando não sobra nada (RN-SIS-06). */
    public static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.strip();
        return t.isEmpty() ? null : t;
    }

    /** Mantém só os dígitos (CPF, CEP e telefone são gravados sem máscara: RN-END-07, RN-PF-03, RN-PES-13). */
    public static String somenteDigitos(String valor) {
        String t = limpar(valor);
        return t == null ? null : t.replaceAll("\\D", "");
    }

    public static String maiusculas(String valor) {
        String t = limpar(valor);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    public static String minusculas(String valor) {
        String t = limpar(valor);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    /** RN-USU-12: login único sem diferenciar maiúsculas; gravado em minúsculas e sem espaços. */
    public static String normalizarLogin(String login) {
        return minusculas(login);
    }

    /** RN-USU-18: a resposta de segurança é comparada sem espaços nas pontas e em minúsculas ("Rex" = "rex "). */
    public static String normalizarResposta(String resposta) {
        return minusculas(resposta);
    }
}
