package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Funcionarios")
public class FuncionarioEntity extends PessoaEntity{
    
    @Column(name = "cargo")
    private String cargo; 
    
    @Column(name = "salario")
    private float salario;
}
