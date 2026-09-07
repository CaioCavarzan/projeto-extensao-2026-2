package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class TintaEntity {
    private int codigo;
    private FornecedorEntity fornecedor; //atributo codigo fk da classe Fornecedor;
    private String descricao;
    private String cor;
    private String tipo;
    private String acabamento;
    private String referenciaFornecedor;
    private int estoque;
    private float valorUnitario;
}

//a