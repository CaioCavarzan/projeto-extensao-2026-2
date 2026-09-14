package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Ceps")
public class CepEntity{

    @Id
    @Column(name = "nr")
    private int numero;

    @Column(name = "cidade_codigo")
    private CidadeEntity cidade; // atributo codigo fk da classe Cidade
}
