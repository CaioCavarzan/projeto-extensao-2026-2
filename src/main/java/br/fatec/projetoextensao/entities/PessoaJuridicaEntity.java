package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "PessoaJuridica")
public class PessoaJuridicaEntity extends ClienteEntity{

    @Column(name = "cnpj")
    private String cnpj;

    @Column(name = "razao_social")
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "ie")
    private int inscricaoEstadual;

}
