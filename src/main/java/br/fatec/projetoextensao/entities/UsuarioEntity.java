package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Usuarios")
public class UsuarioEntity {
    
    @Column(name = "funcionario_codigo")
    private FuncionarioEntity funcionario; //atributo codigo fk da classe Funcionario; 
    
    @Column(name = "login")
    private String login;
    
    @Column(name = "senha")
    private String senha;
    
    @Column(name = "tipo_acesso")
    private String tipoAcesso;
}
