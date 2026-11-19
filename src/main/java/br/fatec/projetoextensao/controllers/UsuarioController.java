package br.fatec.projetoextensao.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.UsuarioResponse;
import br.fatec.projetoextensao.services.UsuarioService;
import lombok.RequiredArgsConstructor;

/**
 * Gestão de usuários, só ADMIN. O usuário é criado pelo próprio funcionário no
 * cadastre-se; aqui o ADMIN muda o perfil e bloqueia ou desbloqueia o acesso,
 * sem tirar o último ADMIN ativo. {codigo} é o código do funcionário dono do acesso.
 */

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @PutMapping("/{codigo}/perfil")
    public UsuarioResponse alterarPerfil(@PathVariable("codigo") int codigo, @RequestParam("admin") boolean admin) {
        return usuarioService.alterarPerfil(codigo, admin);
    }

    @PostMapping("/{codigo}/bloquear")
    public UsuarioResponse bloquear(@PathVariable("codigo") int codigo) {
        return usuarioService.bloquear(codigo);
    }

    @PostMapping("/{codigo}/desbloquear")
    public UsuarioResponse desbloquear(@PathVariable("codigo") int codigo) {
        return usuarioService.desbloquear(codigo);
    }
}
