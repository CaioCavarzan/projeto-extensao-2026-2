package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class ClienteEntity extends PessoaEntity{

    private FonteAquisicaoEntity fonte; //atributo codigo fk da classe FonteAquisicao
    private String observacao;
    private int prioridade;
}
