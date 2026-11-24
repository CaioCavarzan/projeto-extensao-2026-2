package br.fatec.projetoextensao.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lista fixa de status do item e transições permitidas (aba
 * "Fluxo de status" da planilha de regras).
 *
 *   Pendente             -> Aguardando execução, Cancelada
 *   Aguardando execução  -> Pausada, Executada, Cancelada
 *   Pausada              -> Aguardando execução, Cancelada
 *   Executada            -> (nenhum: retrabalho não muda o status, vira nova etapa, RN-ITE-06)
 *   Cancelada            -> (nenhum)
 *
 * Gatilhos (aba "Fluxo de status"): Pendente -> Aguardando execução é automático, quando a rota está definida e
 * os dados obrigatórios estão preenchidos; Aguardando execução -> Executada é automático, ao concluir a última
 * etapa da rota; Pausada e Cancelada são manuais.
 */

public enum StatusItemEnum {

    PENDENTE("Pendente"),
    AGUARDANDO_EXECUCAO("Aguardando execução"),
    PAUSADA("Pausada"),
    EXECUTADA("Executada"),
    CANCELADA("Cancelada");

    private static final Map<StatusItemEnum, Set<StatusItemEnum>> TRANSICOES = new EnumMap<>(StatusItemEnum.class);

    static {
        TRANSICOES.put(PENDENTE, EnumSet.of(AGUARDANDO_EXECUCAO, CANCELADA));
        TRANSICOES.put(AGUARDANDO_EXECUCAO, EnumSet.of(PAUSADA, EXECUTADA, CANCELADA));
        TRANSICOES.put(PAUSADA, EnumSet.of(AGUARDANDO_EXECUCAO, CANCELADA));
        TRANSICOES.put(EXECUTADA, EnumSet.noneOf(StatusItemEnum.class));
        TRANSICOES.put(CANCELADA, EnumSet.noneOf(StatusItemEnum.class));
    }

    private final String nome;

    StatusItemEnum(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    public boolean podeMudarPara(StatusItemEnum novo) {
        return TRANSICOES.get(this).contains(novo);
    }

    public boolean isEncerrado() {
        return this == EXECUTADA || this == CANCELADA;
    }

    public static StatusItemEnum doNome(String nome) {
        for (StatusItemEnum s : values()) {
            if (s.nome.equalsIgnoreCase(nome)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Status de item desconhecido: " + nome);
    }
}
