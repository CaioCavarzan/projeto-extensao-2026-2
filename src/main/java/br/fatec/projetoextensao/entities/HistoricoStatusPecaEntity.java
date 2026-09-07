package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;

@Entity
public class HistoricoStatusPecaEntity{

    private PecaEntity peca; // Atributo codigo fk da classe Peca
    private StatusPecaEntity statusPeca; // Atributo codigo fk da classe StatusPeca
    private LocalDateTime dataHora;
}
