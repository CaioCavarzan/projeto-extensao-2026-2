package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Clientes")
public class ClienteEntity extends PessoaEntity{

    @Column(name = "fonte_codigo")
    private FonteAquisicaoEntity fonte; //atributo codigo fk da classe FonteAquisicao
    
    @Column(name = "observacao")
    private String observacao;

    @Column(name = "prioridade")
    private int prioridade;
}
