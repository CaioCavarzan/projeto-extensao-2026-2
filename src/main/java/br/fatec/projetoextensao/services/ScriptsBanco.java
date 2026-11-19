package br.fatec.projetoextensao.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;


final class ScriptsBanco {

    static final String PASTA = "bancodados/";

    private static final Pattern LINHA_GO = Pattern.compile("^\\s*go\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMENTARIO_LINHA = Pattern.compile("--[^\\r\\n]*");
    private static final Pattern COMENTARIO_BLOCO = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private ScriptsBanco() {
    }

    static String ler(String nomeArquivo) {
        try (InputStream in = new ClassPathResource(PASTA + nomeArquivo).getInputStream()) {
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return texto.startsWith("﻿") ? texto.substring(1) : texto;
        } catch (IOException e) {
            throw new IllegalStateException("Script " + nomeArquivo + " não encontrado no classpath (" + PASTA
                    + "). Confira o <resources> do pom.xml (docs/database).", e);
        }
    }

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

    static boolean ehUse(String lote) {
        return semComentarios(lote).toLowerCase(Locale.ROOT).matches("use\\s+\\S+;?");
    }

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
