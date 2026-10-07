package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "Usuarios")
public class UsuarioEntity {
    
    @Column(name = "funcionario_codigo")
    private FuncionarioEntity funcionario;

    //colocar aqui atributo de 'is_admin'
    
    @Column(name = "login")
    private String login;
    
    @Column(name = "senha_hash")
    private String senhaHash;
    
    //colocar aqui atributo de 'ativo'

}
