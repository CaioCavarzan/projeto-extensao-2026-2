package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "TipoServico")
public class TipoServicoEntity {

    @Column(name = "codigo")
    private int codigo;

    @Column(name = "tipo")
    private String tipo;
}
