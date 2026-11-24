package br.fatec.projetoextensao.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lista fixa de status da OS e transições permitidas (aba
 * "Fluxo de status" da planilha de regras, que fecha a D3 do cronograma).
 *
 * O nome de cada constante é exatamente o texto gravado na tabela StatusOs. O Java nunca usa o código numérico
 * do status, porque ele depende da ordem em que as linhas foram inseridas no banco.
 *
 *   Criada       -> Em produção, Cancelada
 *   Em produção  -> Pausada, Concluída, Cancelada
 *   Pausada      -> Em produção, Cancelada
 *   Concluída    -> (nenhum: OS concluída não reabre)
 *   Cancelada    -> (nenhum)
 */

public enum StatusOsEnum {

    CRIADA("Criada"),
    EM_PRODUCAO("Em produção"),
    PAUSADA("Pausada"),
    CONCLUIDA("Concluída"),
    CANCELADA("Cancelada");

    private static final Map<StatusOsEnum, Set<StatusOsEnum>> TRANSICOES = new EnumMap<>(StatusOsEnum.class);

    static {
        TRANSICOES.put(CRIADA, EnumSet.of(EM_PRODUCAO, CANCELADA));
        TRANSICOES.put(EM_PRODUCAO, EnumSet.of(PAUSADA, CONCLUIDA, CANCELADA));
        TRANSICOES.put(PAUSADA, EnumSet.of(EM_PRODUCAO, CANCELADA));
        TRANSICOES.put(CONCLUIDA, EnumSet.noneOf(StatusOsEnum.class));
        TRANSICOES.put(CANCELADA, EnumSet.noneOf(StatusOsEnum.class));
    }

    private final String nome;

    StatusOsEnum(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    public boolean podeMudarPara(StatusOsEnum novo) {
        return TRANSICOES.get(this).contains(novo);
    }

    public boolean isEncerrado() {
        return this == CONCLUIDA || this == CANCELADA;
    }

    public static StatusOsEnum doNome(String nome) {
        for (StatusOsEnum s : values()) {
            if (s.nome.equalsIgnoreCase(nome)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Status de OS desconhecido: " + nome);
    }
}
