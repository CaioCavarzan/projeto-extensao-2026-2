package br.fatec.projetoextensao.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.config.ParametrosSistema;

/**
 * Guarda os arquivos das fotos numa pasta do servidor. O banco guarda só o
 * caminho relativo (coluna Fotos.foto).
 */

@Component
public class ArmazenamentoFotos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoFotos.class);

    private final Path raiz;

    public ArmazenamentoFotos(ParametrosSistema parametros) {
        this.raiz = Paths.get(parametros.getDiretorioFotos()).toAbsolutePath().normalize();
    }

    public String gravar(byte[] conteudo, String extensao) throws IOException {
        Files.createDirectories(raiz);
        String nome = UUID.randomUUID() + "." + extensao; // RN-FOT-08
        Files.write(raiz.resolve(nome), conteudo, StandardOpenOption.CREATE_NEW);
        return nome;
    }

    public void apagar(String caminhoRelativo) {
        try {
            Path arquivo = resolver(caminhoRelativo);
            Files.deleteIfExists(arquivo);
        } catch (IOException | RuntimeException e) {
            log.warn("Não foi possível apagar o arquivo da foto {}: {}", caminhoRelativo, e.getMessage());
        }
    }

    public Path resolver(String caminhoRelativo) {
        Path arquivo = raiz.resolve(caminhoRelativo).normalize();
        if (!arquivo.startsWith(raiz)) {
            throw new IllegalArgumentException("Caminho de foto fora da pasta das fotos.");
        }
        return arquivo;
    }
}
