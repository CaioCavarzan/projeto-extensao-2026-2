package br.fatec.projetoextensao.entities;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;

import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "Tintas")
public class TintaEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fornecedor_codigo")
    private FornecedorEntity fornecedor; 

    @ManyToOne(optional = false)
    @JoinColumn(name = "cor_codigo")
    private CorEntity cor; 
    
    @Column(name = "descricao")
    private String descricao;
        
    @Column(name = "tipo")
    private String tipo;
    
    @Column(name = "acabamento")
    private String acabamento;
    
    @Column(name = "ref_fornecedor")
    private String referenciaFornecedor;
    
    @Column(name = "estoque_atual")
    private BigDecimal estoque = BigDecimal.ZERO;
    
    @Column(name = "valor_unit")
    private BigDecimal valorUnitario;
    
}
