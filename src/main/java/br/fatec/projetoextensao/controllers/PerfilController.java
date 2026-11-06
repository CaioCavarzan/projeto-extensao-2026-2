package br.fatec.projetoextensao.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.AlterarPerguntaRequest;
import br.fatec.projetoextensao.dtos.AlterarSenhaRequest;
import br.fatec.projetoextensao.dtos.PerfilRequest;
import br.fatec.projetoextensao.dtos.PerfilResponse;
import br.fatec.projetoextensao.services.UsuarioService;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (21/10/2026): "Meu perfil" do usuário logado (ADMIN ou OPERADOR): contatos, senha e pergunta de segurança.
 */
@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final UsuarioService usuarioService;

    @GetMapping
    public PerfilResponse meuPerfil() {
        return usuarioService.meuPerfil();
    }

    @PutMapping
    public PerfilResponse atualizar(@RequestBody PerfilRequest dto) {
        return usuarioService.atualizarMeuPerfil(dto);
    }

    @PutMapping("/senha")
    public ResponseEntity<Void> alterarSenha(@RequestBody AlterarSenhaRequest dto) {
        usuarioService.alterarSenha(dto);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/pergunta-seguranca")
    public ResponseEntity<Void> alterarPergunta(@RequestBody AlterarPerguntaRequest dto) {
        usuarioService.alterarPerguntaSeguranca(dto);
        return ResponseEntity.noContent().build();
    }
}
