package br.fatec.projetoextensao.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.fatec.projetoextensao.dtos.EntregaRequest;
import br.fatec.projetoextensao.dtos.EtapaResponse;
import br.fatec.projetoextensao.dtos.FotoResponse;
import br.fatec.projetoextensao.dtos.HistoricoStatusResponse;
import br.fatec.projetoextensao.dtos.ItemAtualizacaoRequest;
import br.fatec.projetoextensao.dtos.ItemResponse;
import br.fatec.projetoextensao.dtos.NovaEtapaRequest;
import br.fatec.projetoextensao.dtos.TintaItemRequest;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;
import br.fatec.projetoextensao.services.ItemRotaProcessoService;
import br.fatec.projetoextensao.services.ItemService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): itens da OS (na tela: "Peças"), com a rota de etapas e as fotos.
 * Item encerrado não é alterado, exceto a entrega (nr_ose) e as fotos (RN-ITE-07 e RN-OS-07).
 * Fotos: POST /{codigo}/fotos com o arquivo no campo "arquivo" (multipart); só JPG ou PNG de até 10 MB, conferidos
 * pelo conteúdo, e no máximo 10 por item (RN-FOT-02, RN-FOT-06 e RN-FOT-07). O nome do arquivo enviado não é usado
 * (RN-FOT-08).
 */
@RestController
@RequestMapping("/api/itens")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemRotaProcessoService itemRotaProcessoService;

    @GetMapping
    public List<ItemResponse> listar() {
        return itemService.listarTodos();
    }

    @GetMapping("/atrasados")
    public List<ItemResponse> atrasados() {
        return itemService.listarAtrasados();
    }

    @GetMapping("/{codigo}")
    public ItemResponse buscar(@PathVariable("codigo") int codigo) {
        return itemService.buscar(codigo);
    }

    @PutMapping("/{codigo}")
    public ItemResponse atualizar(@PathVariable("codigo") int codigo, @RequestBody ItemAtualizacaoRequest dto) {
        return itemService.atualizar(codigo, dto);
    }

    @PutMapping("/{codigo}/tinta")
    public ItemResponse definirTinta(@PathVariable("codigo") int codigo, @RequestBody TintaItemRequest dto) {
        return itemService.definirTinta(codigo, dto == null ? null : dto.tintaCodigo());
    }

    @PostMapping("/{codigo}/pausar")
    public ItemResponse pausar(@PathVariable("codigo") int codigo) {
        return itemService.pausar(codigo);
    }

    @PostMapping("/{codigo}/retomar")
    public ItemResponse retomar(@PathVariable("codigo") int codigo) {
        return itemService.retomar(codigo);
    }

    @PostMapping("/{codigo}/cancelar")
    public ItemResponse cancelar(@PathVariable("codigo") int codigo) {
        return itemService.cancelar(codigo);
    }

    @PutMapping("/{codigo}/entrega")
    public ItemResponse registrarEntrega(@PathVariable("codigo") int codigo, @RequestBody EntregaRequest dto) {
        return itemService.registrarEntrega(codigo, dto == null ? null : dto.nrOse());
    }

    @GetMapping("/{codigo}/historico")
    public List<HistoricoStatusResponse> historico(@PathVariable("codigo") int codigo) {
        return itemService.historico(codigo);
    }

    @GetMapping("/{codigo}/etapas")
    public List<EtapaResponse> etapas(@PathVariable("codigo") int codigo) {
        return itemRotaProcessoService.listarDoItem(codigo);
    }

    @PostMapping("/{codigo}/etapas")
    public ResponseEntity<List<EtapaResponse>> adicionarEtapa(@PathVariable("codigo") int codigo,
                                                              @RequestBody NovaEtapaRequest dto) {
        if (dto == null || dto.rotaCodigo() == null) {
            throw new RegraNegocioException("Escolha a etapa.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(itemRotaProcessoService.adicionarEtapa(codigo, dto.rotaCodigo()));
    }

    @GetMapping("/{codigo}/fotos")
    public List<FotoResponse> fotos(@PathVariable("codigo") int codigo) {
        return itemService.listarFotos(codigo);
    }

    @PostMapping(value = "/{codigo}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoResponse> anexarFoto(@PathVariable("codigo") int codigo,
                                                   @RequestParam(name = "arquivo", required = false) MultipartFile arquivo)
            throws IOException {
        byte[] conteudo = arquivo == null ? null : arquivo.getBytes();
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.anexarFoto(codigo, conteudo));
    }
}
