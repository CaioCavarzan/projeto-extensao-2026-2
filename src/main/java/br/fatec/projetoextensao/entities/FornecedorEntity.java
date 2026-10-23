package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "Fornecedores")
public class FornecedorEntity{

    @Id
    private int pessoaCodigo;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "pessoa_codigo")
    private PessoaEntity pessoa;

    @Column(name = "cnpj")
    private String cnpj;

    @Column(name = "ativo")
    private boolean ativo = true;

    @Column(name = "prazo_pagamento")
    private String prazoPagamento;

   }
