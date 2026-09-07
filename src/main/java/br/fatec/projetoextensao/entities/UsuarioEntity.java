package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class UsuarioEntity {
    private FuncionarioEntity funcionario; //atributo codigo fk da classe Funcionario; 
    private String login;
    private String senha;
    private String tipoAcesso;
}


//a