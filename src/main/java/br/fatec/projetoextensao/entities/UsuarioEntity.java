package br.fatec.projetoextensao.entities;

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
@Table(name = "Usuarios")
public class UsuarioEntity{
    
    @Id
    private int funcionarioCodigo;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "funcionario_codigo")
    private FuncionarioEntity funcionario;

    @Column(name = "is_admin")
    private boolean isAdmin;
    
    @Column(name = "login")
    private String login;
    
    @Column(name = "senha_hash")
    private String senhaHash;
    
    @Column(name = "pergunta_seguranca")
    private String perguntaSeguranca;

    @Column(name = "resposta_hash")
    private String respostaHash;
    
    @Column(name = "ativo")
    private boolean ativo = true;

}
