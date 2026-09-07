package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FotoEntity{

    @Id
    private int codigo;
    private PecaEntity peca; //atributo codigo fk da classe Peca;
    private String foto;
}
