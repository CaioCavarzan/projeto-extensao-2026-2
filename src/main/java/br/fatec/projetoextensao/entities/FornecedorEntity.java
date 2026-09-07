package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class FornecedorEntity extends PessoaEntity{
    
    private String cnpj;
    private boolean status;
    private String prazoPagamento; 
   }
