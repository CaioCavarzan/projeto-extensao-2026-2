package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;

@Entity

public class ProducaoEntity {
    private int codigo;
    private Cronograma cronograma; //Atributo código fk da classe Cronograma 
    private ServicoAplicadoPeca servicoAplicadoPeca; //Atributo código fk da classe ServicoAplicadoPeca
    private Usuario usuarioCadastrou;
    private LocalDateTime dataExecucao;
    private int qtdProduzida;
    private int qtdDefeito;
    private String observacao;

}

//a