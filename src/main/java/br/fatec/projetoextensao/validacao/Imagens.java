package br.fatec.projetoextensao.validacao;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * INCLUÍDO (20/10/2026): conferência e redução das fotos dos itens (RN-FOT-07 e RN-FOT-09).
 * Usa só o Java padrão (javax.imageio), sem biblioteca nova no pom.xml.
 */
public final class Imagens {

    public enum Tipo { JPG, PNG }

    private static final byte[] ASSINATURA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private Imagens() {
    }

    /**
     * RN-FOT-07: o tipo é conferido pelo conteúdo (primeiros bytes), não pela extensão nem pelo content-type
     * enviado. JPG começa com FF D8 FF e PNG com 89 50 4E 47 0D 0A 1A 0A. Devolve null se não for nenhum dos dois.
     */
    public static Tipo detectarTipo(byte[] conteudo) {
        if (comecaCom(conteudo, ASSINATURA_JPG)) {
            return Tipo.JPG;
        }
        if (comecaCom(conteudo, ASSINATURA_PNG)) {
            return Tipo.PNG;
        }
        return null;
    }

    private static boolean comecaCom(byte[] conteudo, byte[] assinatura) {
        if (conteudo == null || conteudo.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if (conteudo[i] != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    /** RN-FOT-07: além da assinatura, o arquivo precisa abrir como imagem. Devolve null se não abrir. */
    public static BufferedImage ler(byte[] conteudo) {
        try {
            return ImageIO.read(new ByteArrayInputStream(conteudo));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * RN-FOT-09: reduz a imagem para no máximo {@code ladoMaximo} pixels no lado maior (mantendo a proporção) e
     * grava como JPG com a qualidade indicada (0.8 = 80%). PNG com fundo transparente ganha fundo branco, porque
     * o JPG não tem transparência.
     */
    public static byte[] reduzirParaJpg(BufferedImage original, int ladoMaximo, float qualidade) throws IOException {
        int largura = original.getWidth();
        int altura = original.getHeight();
        double escala = Math.min(1.0, (double) ladoMaximo / Math.max(largura, altura));
        int novaLargura = Math.max(1, (int) Math.round(largura * escala));
        int novaAltura = Math.max(1, (int) Math.round(altura * escala));

        BufferedImage destino = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, novaLargura, novaAltura);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, novaLargura, novaAltura, null);
        } finally {
            g.dispose();
        }

        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpg");
        if (!escritores.hasNext()) {
            throw new IOException("Nenhum gravador de JPG disponível.");
        }
        ImageWriter escritor = escritores.next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(saida)) {
            escritor.setOutput(ios);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(qualidade);
            escritor.write(null, new IIOImage(destino, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }
}
