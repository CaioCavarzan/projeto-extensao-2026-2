package br.fatec.projetoextensao.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "Funcionarios")
public class FuncionarioEntity{

    @Id
    private int pessoaCodigo;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "pessoa_codigo")
    private PessoaEntity pessoa;

    @Column(name = "cpf")
    private String cpf;

    @Column(name = "cargo")
    private String cargo;

    @Column(name = "salario")
    private BigDecimal salario;

    @Column(name = "data_admissao")
    private LocalDate dataAdmissao;

}
