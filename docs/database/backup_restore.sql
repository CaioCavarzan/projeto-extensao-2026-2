-- =================================================================================================
-- arquivo : docs/database/backup_restore.sql
-- banco   : banco_fenix (sql server)
-- objetos : 1. rotina de backup completo (backup database + restore verifyonly)
--           2. rotina de restore completo (single_user with rollback immediate + restore database)
-- =================================================================================================

use master
go

-- -------------------------------------------------------------------------------------------------
-- 1. rotina de backup completo (full backup) e verificação de integridade
-- finalidade : identifica o diretório padrão de backup da instância do sql server, gera o arquivo
--              banco_fenix_full.bak de forma online e valida se o conjunto de backup está íntegro.
-- -------------------------------------------------------------------------------------------------
declare @diretorio_backup nvarchar(400),
        @caminho_arquivo  nvarchar(500);

exec master.dbo.xp_instance_regread
    N'HKEY_LOCAL_MACHINE',
    N'Software\Microsoft\MSSQLServer\MSSQLServer',
    N'BackupDirectory',
    @diretorio_backup output;

set @caminho_arquivo = concat(@diretorio_backup, N'\banco_fenix_full.bak');

backup database banco_fenix
to disk = @caminho_arquivo
with
    format,
    init,
    name  = N'banco_fenix - Backup Completo',
    stats = 10;

restore verifyonly
from disk = @caminho_arquivo;
go

-- -------------------------------------------------------------------------------------------------
-- 2. rotina de restore completo (restauração sem bloqueios)
-- finalidade : desconecta sessões ativas colocando o banco em modo single_user with rollback
--              immediate, restaura o banco a partir do arquivo .bak substituindo a base atual e
--              retorna o banco para modo multi_user.
-- -------------------------------------------------------------------------------------------------
declare @diretorio_backup nvarchar(400),
        @caminho_arquivo  nvarchar(500);

exec master.dbo.xp_instance_regread
    N'HKEY_LOCAL_MACHINE',
    N'Software\Microsoft\MSSQLServer\MSSQLServer',
    N'BackupDirectory',
    @diretorio_backup output;

set @caminho_arquivo = concat(@diretorio_backup, N'\banco_fenix_full.bak');

if db_id(N'banco_fenix') is not null
begin
    alter database banco_fenix
    set single_user with rollback immediate;
end;

restore database banco_fenix
from disk = @caminho_arquivo
with
    replace,
    recovery,
    stats = 10;

alter database banco_fenix
set multi_user;
go
