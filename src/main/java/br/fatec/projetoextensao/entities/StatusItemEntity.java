package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Table(name = "StatusItens")
public class StatusItemEntity{
    
    @Column(name = "peca_codigo")
    private int codigo;

    @Column(name = "status_peca_codigo")
    private String status;
    
}