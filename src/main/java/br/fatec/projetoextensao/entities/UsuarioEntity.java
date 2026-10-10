package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "Usuarios")
public class UsuarioEntity{
    
    @Column(name = "funcionario_codigo")
    private FuncionarioEntity funcionario;

    @Column(name = "is_admin")
    private boolean isAdmin;
    
    @Column(name = "login")
    private String login;
    
    @Column(name = "senha_hash")
    private String senhaHash;
    
    @Column(name = "ativo")
    private boolean ativo;

}
