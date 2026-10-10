package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@PrimaryKeyJoinColumn(name = "pessoa_codigo")
@Table(name = "Fornecedores")
public class FornecedorEntity extends PessoaEntity{
    
    @Column(name = "cnpj")
    private String cnpj;
    
    @Column(name = "ativo")
    private boolean ativo;

    @Column(name = "prazo_pagamento")
    private String prazoPagamento; 
    
   }
