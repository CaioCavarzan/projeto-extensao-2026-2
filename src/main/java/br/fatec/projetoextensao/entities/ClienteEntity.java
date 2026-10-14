package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@PrimaryKeyJoinColumn(name = "pessoa_codigo")
@Table(name = "Clientes")
public class ClienteEntity extends PessoaEntity{

    @ManyToOne(optional = false)
    @JoinColumn(name = "fonte_codigo")
    private FonteAquisicaoEntity fonte; 
    
    @Column(name = "observacao")
    private String observacao;

    @Column(name = "prioridade")
    private int prioridade;
    
}
