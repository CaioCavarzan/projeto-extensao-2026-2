package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Cronograma_Status")
public class HistoricoStatusCronogramaEntity{

    @Column(name = "cronograma_codigo")
    private CronogramaEntity cronograma; // Atributo codigo fk da classe Cronograma
    
    @Column(name = "status_cronograma_codigo")
    private StatusCronogramaEntity statusCronograma; // Atributo codigo fk da classe StatusCronograma
    
    @Column(name = "data_hora")
    private LocalDateTime dataHora;
    
    @Column(name = "observacao")
    private String observacao;
}
