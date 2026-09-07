package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class PessoaJuridicaEntity extends Cliente{
    private String cnpj;
    private String razaoSocial;
    private String nomeFantasia;
    private int inscricaoEstadual;

}

//a