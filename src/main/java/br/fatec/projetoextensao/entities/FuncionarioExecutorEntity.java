package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "FuncionarioExecutores")
public class FuncionarioExecutorEntity{

    @Column(name = "producao_codigo")
    private ProducaoEntity producao; // Atributo código fk da classe Producao
    
    @Column(name = "funcionario_codigo")
    private FuncionarioEntity funcionario; // Atributo código fk da classe Funcionario
}
