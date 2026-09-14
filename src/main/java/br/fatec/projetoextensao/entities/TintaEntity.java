package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Tintas")
public class TintaEntity {
    
    @Column(name = "codigo")
    private int codigo;

    @Column(name = "fornecedor_codigo")
    private FornecedorEntity fornecedor; //atributo codigo fk da classe Fornecedor;
    
    @Column(name = "descricao")
    private String descricao;
    
    @Column(name = "cor")
    private String cor;
    
    @Column(name = "tipo")
    private String tipo;
    
    @Column(name = "acabamento")
    private String acabamento;
    
    @Column(name = "ref_fornecedor")
    private String referenciaFornecedor;
    
    @Column(name = "estoque")
    private int estoque;
    
    @Column(name = "valor_unit")
    private float valorUnitario;
}
