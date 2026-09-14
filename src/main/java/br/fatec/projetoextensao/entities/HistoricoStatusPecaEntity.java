package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Pecas_Status")
public class HistoricoStatusPecaEntity{

    @Column(name = "peca_codigo")
    private PecaEntity peca; // Atributo codigo fk da classe Peca

    @Column(name = "status_peca_codigo")
    private StatusPecaEntity statusPeca; // Atributo codigo fk da classe StatusPeca

    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
