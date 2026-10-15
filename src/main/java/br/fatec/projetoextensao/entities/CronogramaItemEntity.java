package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import java.math.BigDecimal;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "CronogramasItens")
@IdClass(CronogramaItemId.class)
public class CronogramaItemEntity{
    
    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "cronograma_codigo")
    private CronogramaEntity cronograma; 

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "item_rota_processo_codigo")
    private ItemRotaProcessoEntity itemRotaProcesso;

    @Column(name = "qtd_planejado")
    private BigDecimal qtdPlanejado;

    @Column(name = "posicao")
    private int posicao;

    @Column(name = "pontuacao")
    private BigDecimal pontuacao;

}
