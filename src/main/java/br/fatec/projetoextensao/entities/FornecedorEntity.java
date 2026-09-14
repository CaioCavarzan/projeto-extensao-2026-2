package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Fornecedores")
public class FornecedorEntity extends PessoaEntity{
    
    @Column(name = "cnpj")
    private String cnpj;
    
    @Column(name = "status")
    private boolean status;

    @Column(name = "prazo_pagamento")
    private String prazoPagamento; 
   }
