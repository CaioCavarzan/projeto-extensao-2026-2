package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;

@Entity
public class HistoricoStatusOsEntity{
    
    private OrdemServicoEntity os; // Atributo codigo fk da classe OrdemServico
    private StatusOsEntity status; // Atributo codigo fk da classe StatusOs
    private LocalDateTime dataHora;
}
