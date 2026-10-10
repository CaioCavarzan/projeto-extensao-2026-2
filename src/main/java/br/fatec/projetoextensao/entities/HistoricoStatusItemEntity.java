package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "HistoricosStatusItens")
public class HistoricoStatusItemEntity{

    @Column(name = "item_codigo")
    private ItemEntity item;

    @Column(name = "status_item_codigo")
    private StatusItemEntity statusPeca; 

    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
