package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "StatusOs")
public class StatusOsEntity {
    
    @Column(name = "os_codigo")
    private int codigo;
    
    @Column(name = "status_codigo")
    private String status;

}