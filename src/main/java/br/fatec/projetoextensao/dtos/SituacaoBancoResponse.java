package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * O que a aba Configurações > Banco de dados mostra: nome do banco e versão do SQL
 * Server, quantos dos 13 objetos da Etapa 3 estão instalados (e quais) e o último backup completo registrado.
 */

public record SituacaoBancoResponse(
        String banco,
        String versaoSqlServer,
        int objetosInstalados,
        int objetosEsperados,
        List<ObjetoBancoResponse> objetos,
        BackupResponse ultimoBackup) {
}
