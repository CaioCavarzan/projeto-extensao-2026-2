package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Os_Status")
public class HistoricoStatusOsEntity{
    
    @Column(name = "os_codigo")
    private OrdemServicoEntity os; // Atributo codigo fk da classe OrdemServico
    
    @Column(name = "status_codigo")
    private StatusOsEntity status; // Atributo codigo fk da classe StatusOs
    
    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
