package br.fatec.projetoextensao.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.AtivarContaRequest;
import br.fatec.projetoextensao.dtos.PerguntaSegurancaResponse;
import br.fatec.projetoextensao.dtos.RedefinirSenhaRequest;
import br.fatec.projetoextensao.dtos.UsuarioResponse;
import br.fatec.projetoextensao.services.UsuarioService;
import lombok.RequiredArgsConstructor;

/**
 * Rotas públicas: POST /ativar-conta (cadastre-se), GET /pergunta-seguranca e
 * POST /redefinir-senha (lembrete de senha).
 * GET /me é o "entrar" do front: com o cabeçalho Authorization (HTTP Basic), devolve o usuário logado e o perfil;
 * login ou senha errados recebem 401; usuário bloqueado, 401 com outra mensagem.
 */

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;

    @PostMapping("/ativar-conta")
    public ResponseEntity<UsuarioResponse> ativarConta(@RequestBody AtivarContaRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.ativarConta(dto));
    }

    @GetMapping("/pergunta-seguranca")
    public PerguntaSegurancaResponse perguntaSeguranca(@RequestParam("login") String login) {
        return new PerguntaSegurancaResponse(usuarioService.buscarPerguntaSeguranca(login));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@RequestBody RedefinirSenhaRequest dto) {
        usuarioService.redefinirSenha(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UsuarioResponse eu() {
        return usuarioService.dadosDoUsuarioLogado();
    }
}
