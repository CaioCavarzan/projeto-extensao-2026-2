package br.fatec.projetoextensao.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.config.ParametrosSistema;
import br.fatec.projetoextensao.dtos.ParametrosResponse;
import br.fatec.projetoextensao.services.FornecedorService;
import br.fatec.projetoextensao.services.FuncionarioService;
import br.fatec.projetoextensao.services.TintaService;
import br.fatec.projetoextensao.services.UsuarioService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): parâmetros que a tela usa para montar listas e conferir antes de enviar. Os valores vêm do
 * application.properties (fenix.*) e das listas fixas dos Services, então a tela acompanha qualquer mudança sem ser
 * editada. Exige login como toda a API (RN-SIS-01); não há dado sensível.
 */
@RestController
@RequestMapping("/api/parametros")
@RequiredArgsConstructor
public class ParametrosController {

    private final ParametrosSistema parametros;

    @GetMapping
    public ParametrosResponse parametros() {
        return new ParametrosResponse(parametros.getPrioridadeMinima(), parametros.getPrioridadeMaxima(),
                parametros.getUnidadesItem(), parametros.getMaxFotosPorItem(), parametros.getTamanhoMaximoFotoMb(),
                FuncionarioService.CARGOS, FornecedorService.PRAZOS_PAGAMENTO, TintaService.UNIDADE_ESTOQUE,
                UsuarioService.SENHA_MINIMO, UsuarioService.SENHA_MAXIMO);
    }
}
