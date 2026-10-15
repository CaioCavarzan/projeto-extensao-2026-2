package br.fatec.projetoextensao.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "Itens")
public class ItemEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "os_codigo")
    private OrdemServicoEntity os;

    @ManyToOne
    @JoinColumn(name = "tinta_codigo")
    private TintaEntity tinta; 

    @ManyToOne
    @JoinColumn(name = "cor_codigo")
    private CorEntity cor;
    
    @Column(name = "descricao")
    private String descricao;

    @Column(name = "qtd_total")
    private BigDecimal qtdTotal;

    @Column(name = "unidade")
    private String unidade;

    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;
    
    @PrePersist
    private void preencherDataEntrada(){
     if (dataEntrada == null) {
         dataEntrada = LocalDateTime.now();}
     }

    @Column(name = "prazo_entrega")
    private LocalDate prazoEntrega;

    @Column(name = "valor_total_item")
    private BigDecimal valorTotalPeca;

    @Column(name = "data_finalizacao")
    private LocalDateTime dataFinalizacao;
    
    @Column(name = "nr_ose")
    private String numeroOse;
    
    @Column(name = "score_prioridade")
    private BigDecimal scorePrioridade;

}
