package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class CidadeEntity{
    
    @Id
    private String codigo;
    private String nome;
    private UfEntity uf; //atributo codigo fk da classe Uf
}
