package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.fatec.projetoextensao.dtos.FotoResponse;
import br.fatec.projetoextensao.dtos.OsResponse;

/** INCLUÍDO (20/10/2026): fotos dos itens (RN-FOT-01 a RN-FOT-09). */
class FotosTest extends TesteBase {

    @Autowired
    private ArmazenamentoFotos armazenamentoFotos;

    private static byte[] imagem(String formato, int largura, int altura) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        return out.toByteArray();
    }

    private int novoItem() {
        OsResponse os = abrirOs(novoClientePF("Cliente Foto " + System.nanoTime()).codigo(), itemJateamento("Peça", null));
        return os.itens().get(0).codigo();
    }

    /** RN-FOT-06 e RN-FOT-07: só JPG ou PNG até 10 MB, conferidos pelo conteúdo e não pela extensão. */
    @Test
    void rnFot06_rnFot07_tipoETamanho() {
        int item = novoItem();
        byte[] pdf = "%PDF-1.4 arquivo".getBytes(StandardCharsets.US_ASCII);
        byte[] executavelRenomeado = "MZ executavel.jpg".getBytes(StandardCharsets.US_ASCII);
        byte[] jpgCorrompido = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};
        byte[] grande = new byte[10 * 1024 * 1024 + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;
        assertThatThrownBy(() -> itemService.anexarFoto(item, pdf)).hasMessage("Envie uma imagem JPG ou PNG de até 10 MB.");
        assertThatThrownBy(() -> itemService.anexarFoto(item, executavelRenomeado)).hasMessage("Envie uma imagem JPG ou PNG de até 10 MB.");
        assertThatThrownBy(() -> itemService.anexarFoto(item, grande)).hasMessage("Envie uma imagem JPG ou PNG de até 10 MB.");
        assertThatThrownBy(() -> itemService.anexarFoto(item, jpgCorrompido)).hasMessage("Arquivo inválido. Envie uma imagem JPG ou PNG.");
    }

    /** RN-FOT-08 e RN-FOT-09: nome gerado (UUID.jpg) e imagem reduzida a 1920 px no lado maior, gravada em JPG. */
    @Test
    void rnFot08_rnFot09_nomeGeradoEReducao() throws IOException {
        int item = novoItem();
        FotoResponse foto = itemService.anexarFoto(item, imagem("png", 3000, 2000));
        assertThat(foto.caminho()).matches("[0-9a-f\\-]{36}\\.jpg");
        Path arquivo = armazenamentoFotos.resolver(foto.caminho());
        BufferedImage gravada = ImageIO.read(arquivo.toFile());
        assertThat(gravada.getWidth()).isEqualTo(1920);
        assertThat(gravada.getHeight()).isEqualTo(1280);
        byte[] inicio = Arrays.copyOf(Files.readAllBytes(arquivo), 3);
        assertThat(inicio).containsExactly((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    /** RN-FOT-02: no máximo 10 fotos por item. */
    @Test
    void rnFot02_limiteDeFotos() throws IOException {
        int item = novoItem();
        byte[] png = imagem("png", 40, 30);
        for (int i = 0; i < 10; i++) {
            itemService.anexarFoto(item, png);
        }
        assertThat(itemService.listarFotos(item)).hasSize(10);
        assertThatThrownBy(() -> itemService.anexarFoto(item, png))
                .hasMessage("Este item já tem 10 fotos. Exclua uma para anexar outra.");
    }

    /** RN-ITE-07 e RN-OS-07: fotos podem ser anexadas mesmo com a OS encerrada. */
    @Test
    void rnOs07_fotoEmOsCancelada() throws IOException {
        OsResponse os = abrirOs(novoClientePF("Cliente Cancelado").codigo(), itemJateamento("Peça", null));
        ordemServicoService.cancelar(os.numero());
        assertThat(itemService.anexarFoto(os.itens().get(0).codigo(), imagem("jpg", 50, 50)).codigo()).isPositive();
    }

    /** RN-FOT-01: excluir a foto apaga também o arquivo (depois de confirmada a exclusão no banco). */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rnFot01_excluirApagaArquivo() throws IOException {
        int item = novoItem();
        FotoResponse foto = itemService.anexarFoto(item, imagem("jpg", 60, 40));
        Path arquivo = armazenamentoFotos.resolver(foto.caminho());
        assertThat(Files.exists(arquivo)).isTrue();
        itemService.removerFoto(foto.codigo());
        assertThat(Files.exists(arquivo)).isFalse();
        assertThat(fotoRepository.findById(foto.codigo())).isEmpty();
    }
}
