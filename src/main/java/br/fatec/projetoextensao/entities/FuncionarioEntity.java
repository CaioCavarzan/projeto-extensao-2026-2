package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class FuncionarioEntity extends PessoaEntity{
    
    private String cargo; 
    private float salario;
}
