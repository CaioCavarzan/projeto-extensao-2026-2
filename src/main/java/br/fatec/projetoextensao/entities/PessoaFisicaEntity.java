package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class PessoaFisicaEntity extends ClienteEntity{
    private String cpf;
    private String nome;

}

//a