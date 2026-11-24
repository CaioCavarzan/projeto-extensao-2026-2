package br.fatec.projetoextensao.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.fatec.projetoextensao.dtos.BackupResponse;
import br.fatec.projetoextensao.dtos.OperacaoBancoResponse;
import br.fatec.projetoextensao.dtos.RestauracaoRequest;
import br.fatec.projetoextensao.dtos.SituacaoBancoResponse;
import br.fatec.projetoextensao.services.BancoDadosService;
import lombok.RequiredArgsConstructor;

/**
 * Aba Configurações > Banco de dados. Todos os métodos do BancoDadosService
 * exigem
 * o perfil ADMIN (@PreAuthorize); o OPERADOR recebe 403.
 * <ul>
 *   <li>GET  /api/banco/situacao: os 13 objetos da Etapa 3 (instalados ou não) e o último backup;</li>
 *   <li>POST /api/banco/objetos/instalar: roda objetos_banco.sql (CREATE OR ALTER dos 13 objetos);</li>
 *   <li>POST /api/banco/backup: rotina 1 do backup_restore.sql (BACKUP DATABASE + RESTORE VERIFYONLY);</li>
 *   <li>POST /api/banco/restauracao: rotina 2 do backup_restore.sql, com {"confirmacao": "RESTAURAR banco_fenix"}.</li>
 * </ul>
 */

@RestController
@RequestMapping("/api/banco")
@RequiredArgsConstructor
public class BancoDadosController {

    private final BancoDadosService bancoDadosService;

    @GetMapping("/situacao")
    public SituacaoBancoResponse situacao() {
        return bancoDadosService.situacao();
    }

    @PostMapping("/objetos/instalar")
    public OperacaoBancoResponse instalarObjetos() {
        return bancoDadosService.instalarObjetos();
    }

    @PostMapping("/backup")
    public BackupResponse backup() {
        return bancoDadosService.fazerBackup();
    }

    @PostMapping("/restauracao")
    public OperacaoBancoResponse restaurar(@RequestBody RestauracaoRequest dto) {
        return bancoDadosService.restaurar(dto);
    }
}
