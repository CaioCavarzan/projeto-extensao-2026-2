package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "Itens")
public class ItemEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;
    
    @Column(name = "os_codigo")
    private OrdemServicoEntity os;

    @Column(name = "tinta_codigo")
    private TintaEntity tinta; 

    @Column(name = "cor_codigo")
    private CorEntity cor;
    
    @Column(name = "descricao")
    private String descricao;

    @Column(name = "qtd_total")
    private float qtdTotal;

    @Column(name = "unidade")
    private String unidade;

    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;

    @Column(name = "prazo_entrega")
    private String prazoEntrega;

    @Column(name = "valor_total_item")
    private float valorTotalPeca;

    @Column(name = "data_finalizacao")
    private LocalDateTime dataFinalizacao;
    
    @Column(name = "numero_ose")
    private String numeroOse;
    
    @Column(name = "score_prioridade")
    private float scorePrioridade;

}
