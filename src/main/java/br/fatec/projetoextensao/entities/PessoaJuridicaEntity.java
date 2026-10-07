package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Table(name = "PessoasJuridicas")
public class PessoaJuridicaEntity extends ClienteEntity {

    @Column(name = "cliente_codigo")
    private ClienteEntity cliente;

    @Column(name = "cnpj")
    private String cnpj;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "ie")
    private int inscricaoEstadual;

}
