package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "CronogramasItens")
public class CronogramaItem {
    
    @Column(name = "cronograma_codigo")
    private CronogramaEntity cronograma;

    @Column(name = "item_rota_processo_codigo")
    private ItemRotaProcessoEntity itemProcesso;

    @Column(name = "qtd_planejado")
    private float qtdPlanejado;

    @Column(name = "posicao")
    private int posicao;

    @Column(name = "pontuacao")
    private float pontuacao;

}
