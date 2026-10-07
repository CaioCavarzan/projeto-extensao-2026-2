package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "Tintas")
public class TintaEntity {
    
    @Column(name = "codigo")
    private int codigo;

    @Column(name = "fornecedor_codigo")
    private FornecedorEntity fornecedor;

    @Column(name = "cor_codigo")
    private String cor;
    
    @Column(name = "descricao")
    private String descricao;
        
    @Column(name = "tipo")
    private String tipo;
    
    @Column(name = "acabamento")
    private String acabamento;
    
    @Column(name = "ref_fornecedor")
    private String referenciaFornecedor;
    
    @Column(name = "estoque_atual")
    private float estoque;
    
    @Column(name = "valor_unit")
    private float valorUnitario;
    
}
