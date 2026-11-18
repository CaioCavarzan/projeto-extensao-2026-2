package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * INCLUÍDO (18/11/2026) [E3-19]: um backup completo do banco, lido do histórico do próprio SQL Server
 * (msdb.dbo.backupset e msdb.dbo.backupmediafamily). arquivo é o caminho do .bak NO SERVIDOR do SQL Server
 * (pasta padrão de backup da instância, a mesma que o script backup_restore.sql lê do registro do Windows).
 * mensagens: o que o SQL Server informou durante a operação (ex.: "10 percent processed.",
 * "The backup set on file 1 is valid."); vazia quando a resposta só consulta o histórico.
 */
public record BackupResponse(
        String banco,
        String arquivo,
        LocalDateTime concluidoEm,
        Long tamanhoBytes,
        List<String> mensagens) {
}
