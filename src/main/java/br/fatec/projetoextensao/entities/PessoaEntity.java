package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "Pessoas")
public class PessoaEntity{

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;

    @Column(name = "email")
    private String email;

    @Column(name = "cep_codigo")
    private CepEntity cep; //atributo codigo fk da classe Cep

    @Column(name = "logradouro")
    private String logradouro;

    @Column(name = "nr_imovel")
    private String numeroImovel;
    
    @Column(name = "telefone")
    private String telefone;

}
