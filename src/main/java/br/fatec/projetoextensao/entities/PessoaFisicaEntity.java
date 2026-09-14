package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "PessoaFisica")
public class PessoaFisicaEntity extends ClienteEntity{

    @Column(name = "cpf")
    private String cpf;

    @Column(name = "nome")
    private String nome;

}
