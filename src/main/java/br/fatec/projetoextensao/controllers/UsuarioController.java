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
 * INCLUÍDO (21/10/2026): gestão de usuários, só ADMIN (RN-SIS-03). O usuário é criado pelo próprio funcionário no
 * cadastre-se (RN-USU-02); aqui o ADMIN muda o perfil (RN-USU-03) e bloqueia ou desbloqueia o acesso (RN-USU-04),
 * sem tirar o último ADMIN ativo (RN-USU-06). {codigo} é o código do funcionário dono do acesso (RN-USU-09).
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
