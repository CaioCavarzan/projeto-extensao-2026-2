package br.fatec.projetoextensao.dtos;

import java.time.LocalDateTime;

/**
 * Situação de um objeto da Etapa 3 no SQL Server (aba Configurações > Banco de dados).
 *
 * instalado: existe em sys.objects com esse nome; tipoConfere: e com o tipo esperado (ex.: TF para
 * fn_prioridade_clientes); habilitado: só para trigger (sys.triggers.is_disabled = 0), null nos demais;
 * alteradoEm: sys.objects.modify_date, para o grupo ver se o objeto é da versão mais nova do script.
 */

public record ObjetoBancoResponse(
        String nome,
        String tipo,
        String script,
        String finalidade,
        String usoNoSistema,
        boolean instalado,
        boolean tipoConfere,
        Boolean habilitado,
        LocalDateTime alteradoEm) {
}
