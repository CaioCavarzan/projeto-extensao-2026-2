package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Peca_Status")
public class StatusPecaEntity{
    
    @Column(name = "peca_codigo")
    private int codigo;

    @Column(name = "status_peca_codigo")
    private String status;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;
    
}