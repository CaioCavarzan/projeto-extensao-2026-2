package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@PrimaryKeyJoinColumn(name = "pessoa_codigo")
@Table(name = "Funcionarios")
public class FuncionarioEntity extends PessoaEntity{

    @Column(name = "cpf")
    private String cpf; 
    
    @Column(name = "cargo")
    private String cargo; 
    
    @Column(name = "salario")
    private float salario;

    @Column(name = "data_admissao")
    private LocalDateTime dataAdmissao;

}
