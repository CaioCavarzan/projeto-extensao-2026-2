package br.fatec.projetoextensao.controllers;

import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.services.ItemService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): arquivo de uma foto (sempre JPG, RN-FOT-09) e exclusão da foto, que apaga também o
 * arquivo (RN-FOT-01). A imagem exige login como o resto da API; por isso o front a busca com o cabeçalho
 * Authorization e a mostra a partir da resposta, e não com um link direto no &lt;img&gt;.
 */
@RestController
@RequestMapping("/api/fotos")
@RequiredArgsConstructor
public class FotoController {

    private final ItemService itemService;

    @GetMapping("/{codigo}/arquivo")
    public ResponseEntity<Resource> arquivo(@PathVariable("codigo") int codigo) {
        Path arquivo = itemService.arquivoDaFoto(codigo);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new FileSystemResource(arquivo));
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> excluir(@PathVariable("codigo") int codigo) {
        itemService.removerFoto(codigo);
        return ResponseEntity.noContent().build();
    }
}
