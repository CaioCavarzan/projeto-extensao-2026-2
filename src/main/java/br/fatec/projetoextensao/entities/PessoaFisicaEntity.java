package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class PessoaFisicaEntity extends Cliente{
    private String cpf;
    private String nome;

}

//a