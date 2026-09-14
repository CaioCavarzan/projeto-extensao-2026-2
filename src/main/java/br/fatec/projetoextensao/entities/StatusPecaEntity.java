package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "StatusPecas")
public class StatusPecaEntity{
    
    @Column(name = "peca_codigo")
    private int codigo;

    @Column(name = "status_peca_codigo")
    private String status;
    
}