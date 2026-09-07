package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class CepEntity{

    @Id
    private int numero;
    private CidadeEntity cidade; //atributo codigo fk da classe Cidade
}
