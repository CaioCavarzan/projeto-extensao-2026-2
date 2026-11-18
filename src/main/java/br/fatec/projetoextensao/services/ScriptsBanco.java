package br.fatec.projetoextensao.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

/**
 * INCLUÍDO (18/11/2026) [E3-16]: leitura dos scripts do grupo (copiados de docs/database para o classpath em
 * bancodados/, ver o pom.xml) e divisão em lotes pelo "GO".
 *
 * Por que dividir: "GO" não é comando SQL. É um separador que o SSMS e o sqlcmd entendem e o driver JDBC não. O
 * SSMS manda ao servidor cada trecho entre dois GO como um lote separado; aqui é feito o mesmo, linha a linha (uma
 * linha que só tem "go"). Isso é obrigatório para os scripts da Etapa 3: CREATE FUNCTION/VIEW/PROCEDURE/TRIGGER
 * precisa ser o primeiro comando do lote.
 */
final class ScriptsBanco {

    /** Pasta do classpath onde o pom.xml coloca os scripts (E3-01). */
    static final String PASTA = "bancodados/";

    private static final Pattern LINHA_GO = Pattern.compile("^\\s*go\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMENTARIO_LINHA = Pattern.compile("--[^\\r\\n]*");
    private static final Pattern COMENTARIO_BLOCO = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private ScriptsBanco() {
    }

    /** Conteúdo do script (UTF-8, como os arquivos do grupo), sem o BOM se o editor tiver gravado um. */
    static String ler(String nomeArquivo) {
        try (InputStream in = new ClassPathResource(PASTA + nomeArquivo).getInputStream()) {
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return texto.startsWith("﻿") ? texto.substring(1) : texto;
        } catch (IOException e) {
            throw new IllegalStateException("Script " + nomeArquivo + " não encontrado no classpath (" + PASTA
                    + "). Confira o <resources> do pom.xml (docs/database).", e);
        }
    }

    /** Lotes do script, na ordem; lotes só com comentário ou em branco são descartados. */
    static List<String> lotes(String script) {
        List<String> lotes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String linha : script.split("\\r?\\n", -1)) {
            if (LINHA_GO.matcher(linha).matches()) {
                adicionar(lotes, atual);
                atual.setLength(0);
            } else {
                atual.append(linha).append('\n');
            }
        }
        adicionar(lotes, atual);
        return lotes;
    }

    /** O lote é só "use <banco>" (troca de banco da conexão)? */
    static boolean ehUse(String lote) {
        return semComentarios(lote).toLowerCase(Locale.ROOT).matches("use\\s+\\S+;?");
    }

    /** Texto do lote sem comentários e com espaços normalizados (para reconhecer o que o lote faz). */
    static String semComentarios(String lote) {
        String t = COMENTARIO_BLOCO.matcher(lote).replaceAll(" ");
        t = COMENTARIO_LINHA.matcher(t).replaceAll(" ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static void adicionar(List<String> lotes, StringBuilder lote) {
        String texto = lote.toString().trim();
        if (!semComentarios(texto).isEmpty()) {
            lotes.add(texto);
        }
    }
}
