package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "HistoricosStatusOs")
public class HistoricoStatusOsEntity{
    
    @Column(name = "os_codigo")
    private OrdemServicoEntity os; 
    
    @Column(name = "status_codigo")
    private StatusOsEntity status; 
    
    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
