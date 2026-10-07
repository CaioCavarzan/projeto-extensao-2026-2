package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "Funcionarios")
public class FuncionarioEntity extends PessoaEntity{

    @Column(name = "pessoa_codigo")
    private PessoaEntity pessoa;

    @Column(name = "cpf")
    private String cpf; 
    
    @Column(name = "cargo")
    private String cargo; 
    
    @Column(name = "salario")
    private float salario;

    @Column(name = "data_admissao")
    private LocalDateTime dataAdmissao;

}
