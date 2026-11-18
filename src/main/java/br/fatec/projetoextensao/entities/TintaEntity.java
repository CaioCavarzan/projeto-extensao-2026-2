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
    
    // ALTERADO (18/11/2026) [E3-07]: era @Column(name = "estoque_atual"), gravado pelo Java (TintaService). Agora o
    // estoque só muda pela procedure dbo.sp_ajustar_estoque_tinta (RN-TIN-14: só por ajuste), que trava a linha
    // (UPDLOCK) e não deixa o saldo ficar negativo. insertable = false e updatable = false: na tinta nova vale o
    // DEFAULT 0 do script (RN-TIN-13) e a alteração do cadastro (descrição, tipo, preço...) não regrava o estoque
    // que estava na memória, o que poderia desfazer um ajuste feito ao mesmo tempo por outro usuário.
    @Column(name = "estoque_atual", insertable = false, updatable = false)
    private BigDecimal estoque = BigDecimal.ZERO;
    
    @Column(name = "valor_unit")
    private BigDecimal valorUnitario;
    
}
