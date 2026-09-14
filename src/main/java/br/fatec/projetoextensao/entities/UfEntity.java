package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Ufs")
public class UfEntity{

    @Column(name = "sigla")
    private String sigla;

    @Column(name = "nome")
    private String nome;
}