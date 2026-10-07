package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "Fornecedores")
public class FornecedorEntity extends PessoaEntity{
    
    @Column(name = "pessoa_codigo")
    private PessoaEntity pessoa;
    
    @Column(name = "cnpj")
    private String cnpj;
    
    @Column(name = "status")
    private boolean status; //verificar por que no dicionário o formato é 'bit' e não 'boolean'

    @Column(name = "prazo_pagamento")
    private String prazoPagamento; 
   }
