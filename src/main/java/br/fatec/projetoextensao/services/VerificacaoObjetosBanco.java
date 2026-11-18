package br.fatec.projetoextensao.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import br.fatec.projetoextensao.dtos.ObjetoBancoResponse;
import br.fatec.projetoextensao.repositories.CatalogoBancoRepository;
import lombok.RequiredArgsConstructor;

/**
 * INCLUÍDO (18/11/2026) [E3-17]: ao iniciar, confere no SQL Server se os 13 objetos da Etapa 3 estão instalados e
 * escreve o resultado no log (no mesmo estilo de VerificacaoListasFixas).
 *
 * Por que só avisa e não instala sozinho: criar objetos no banco é uma decisão do administrador (pode haver uma
 * versão do script sendo testada no SSMS). Sem os objetos, o sistema sobe, mas as telas que dependem deles
 * respondem 503 com o nome do objeto que falta (ObjetoBancoAusenteException). A instalação é feita em Configurações >
 * Banco de dados ou rodando os scripts etapa3_01 a etapa3_04 no SSMS, nessa ordem.
 * No H2 dos testes automáticos a conferência é pulada (os objetos são imitados pelo etapa3-h2.sql).
 */
@Component
@RequiredArgsConstructor
public class VerificacaoObjetosBanco {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoObjetosBanco.class);

    private final CatalogoBancoRepository catalogo;

    @EventListener(ApplicationReadyEvent.class)
    public void conferir() {
        try {
            if (!catalogo.ehSqlServer()) {
                log.info("Conferência dos objetos da Etapa 3 pulada: o banco não é SQL Server (testes automáticos).");
                return;
            }
            List<ObjetoBancoResponse> objetos = catalogo.situacaoObjetos();
            List<String> faltando = objetos.stream().filter(o -> !o.instalado() || !o.tipoConfere())
                    .map(o -> o.nome() + " (" + o.script() + ")").toList();
            List<String> desabilitados = objetos.stream().filter(o -> Boolean.FALSE.equals(o.habilitado()))
                    .map(ObjetoBancoResponse::nome).toList();
            if (faltando.isEmpty()) {
                log.info("Objetos da Etapa 3 no banco: {} de {} instalados.", objetos.size(), objetos.size());
            } else {
                log.error("Objetos da Etapa 3 FALTANDO no banco ({} de {}): {}. Instale em Configurações > Banco de dados "
                        + "ou rode docs/database/etapa3_01 a etapa3_04 no SSMS, nessa ordem.", faltando.size(),
                        objetos.size(), String.join(", ", faltando));
            }
            if (!desabilitados.isEmpty()) {
                log.error("Triggers da Etapa 3 DESABILITADOS: {}. Sem eles o valor da OS e a data de finalização não são "
                        + "atualizados. Habilite com: enable trigger <nome> on <tabela>.", String.join(", ", desabilitados));
            }
        } catch (DataAccessException e) {
            log.warn("Não foi possível conferir os objetos da Etapa 3: {}", e.getMostSpecificCause().getMessage());
        }
    }
}
