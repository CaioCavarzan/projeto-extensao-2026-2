package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "Os_Status")
public class StatusOsEntity {
    
    @Column(name = "os_codigo")
    private int codigo;
    
    @Column(name = "status_codigo")
    private String status;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;

}