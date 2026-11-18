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
 * INCLUÍDO (18/11/2026) [E3-18]: aba Configurações > Banco de dados. Todos os métodos do BancoDadosService exigem
 * o perfil ADMIN (@PreAuthorize, RN-SIS-03); o OPERADOR recebe 403.
 * <ul>
 *   <li>GET  /api/banco/situacao: os 13 objetos da Etapa 3 (instalados ou não) e o último backup;</li>
 *   <li>POST /api/banco/objetos/instalar: roda etapa3_01 a etapa3_04 (CREATE OR ALTER);</li>
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
