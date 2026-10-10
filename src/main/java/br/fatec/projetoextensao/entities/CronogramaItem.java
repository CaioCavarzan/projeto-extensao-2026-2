package br.fatec.projetoextensao.entities;

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
@Table(name = "CronogramasItens")
public class CronogramaItem{
    
    @Column(name = "cronograma_codigo")
    private CronogramaEntity cronograma; //verificar com a tia Lu se é assim que funciona a parte de chave estrangeira

    @Column(name = "item_rota_processo_codigo")
    private ItemRotaProcessoEntity itemProcesso; //verificar com a tia Lu se é assim que funciona a parte de chave estrangeira

    @Column(name = "qtd_planejado")
    private float qtdPlanejado;

    @Column(name = "posicao")
    private int posicao;

    @Column(name = "pontuacao")
    private float pontuacao;

}
