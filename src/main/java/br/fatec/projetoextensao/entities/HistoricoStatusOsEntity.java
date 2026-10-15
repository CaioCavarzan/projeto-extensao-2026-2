package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "HistoricosStatusOs")
@IdClass(HistoricoStatusOsId.class)
public class HistoricoStatusOsEntity{
    
    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "os_codigo")
    private OrdemServicoEntity os; 
    
    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "status_codigo")
    private StatusOsEntity status;

    @Id
    @Column(name = "data_hora")
    private LocalDateTime dataHora;

    @PrePersist
    private void preencherDataHora(){
        if (dataHora == null) {
            dataHora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
