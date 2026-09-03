package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class PessoaEntity {
    private int codigo;
    private String email;
    private Cep cep; //atributo codigo fk da classe Cep
    private String logradouro;
    private String numeroImovel;
    private String telefone;

}