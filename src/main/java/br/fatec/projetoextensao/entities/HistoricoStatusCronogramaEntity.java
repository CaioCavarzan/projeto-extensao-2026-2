package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;

@Entity
public class HistoricoStatusCronogramaEntity{
    
    private CronogramaEntity cronograma; // Atributo codigo fk da classe Cronograma
    private StatusCronogramaEntity statusCronograma; // Atributo codigo fk da classe StatusCronograma
    private LocalDateTime dataHora;
    private String observacao;
}
