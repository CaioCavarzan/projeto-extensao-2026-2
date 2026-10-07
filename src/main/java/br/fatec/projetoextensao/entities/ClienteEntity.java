package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "Clientes")
public class ClienteEntity extends PessoaEntity{

    @Column(name = "pessoa_codigo")
    private PessoaEntity pessoa; 

    @Column(name = "fonte_codigo")
    private FonteAquisicaoEntity fonte; 
    
    @Column(name = "observacao")
    private String observacao;

    @Column(name = "prioridade")
    private int prioridade;
    
}
