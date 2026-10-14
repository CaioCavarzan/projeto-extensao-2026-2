package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "HistoricosStatusItens")
@IdClass(HistoricoStatusItemId.class)
public class HistoricoStatusItemEntity{

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "item_codigo")   
    private ItemEntity item;

    @Id
    @ManyToOne (optional = false)
    @JoinColumn(name = "status_item_codigo")
    private StatusItemEntity statusItem; 

    @Id
    @Column(name = "data_hora")
    private LocalDateTime dataHora;

    @PrePersist
    private void preencherDataHora(){
    if (dataHora == null) {
        dataHora = LocalDateTime.now();
        }
    }
}
