package br.fatec.projetoextensao.dtos;

import java.util.List;

/**
 * INCLUÍDO (21/10/2026). Lista simples de mensagens (ex.: os papéis da pessoa depois de corrigir CPF ou CNPJ).
 */
public record MensagensResponse(List<String> mensagens) {
}
