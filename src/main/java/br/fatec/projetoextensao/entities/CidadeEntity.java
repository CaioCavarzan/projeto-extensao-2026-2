package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Cidades")
public class CidadeEntity{

    @Id
    @Column(name = "codigo")
    private String codigo;

    @Column(name = "nome")
    private String nome;

    @Column(name = "uf_codigo")
    private UfEntity uf; //atributo codigo fk da classe Uf
}
