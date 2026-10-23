/**
 * INCLUÍDO (20/10/2026): objetos que entram e saem da API (records), para que as entidades não fiquem expostas.
 *
 * RN-SIS-05: nenhum *Request traz o que o banco ou o sistema gera: códigos IDENTITY (RN-PES-08, RN-END-04,
 * RN-TIN-03, RN-OS-08, RN-ITE-09, RN-FOT-03, RN-IRP-10, RN-CRO-03...), datas de registro, hashes, valor da OS,
 * score de prioridade, ordem da etapa e usuário que registrou. Em alteração, o código do registro chega ao Service
 * como parâmetro separado (no Controller, virá da URL), nunca dentro do corpo.
 *
 * As mensagens de validação ficam nos Services (pacote validacao), com o texto da coluna "Mensagem" da planilha
 * de regras. O projeto não usa Bean Validation (@NotNull, @Size...): essa dependência não está no pom.xml, e boa
 * parte das regras depende do banco (unicidade, papéis da pessoa, status atual), que só o Service consulta.
 */
package br.fatec.projetoextensao.dtos;
