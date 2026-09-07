package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class FuncionarioExecutorEntity{

    private Producao producao; // Atributo código fk da classe Producao
    private FuncionarioEntity funcionario; // Atributo código fk da classe Funcionario
}
