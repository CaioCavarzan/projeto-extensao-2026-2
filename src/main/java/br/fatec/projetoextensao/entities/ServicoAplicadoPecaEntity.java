package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class ServicoAplicadoPecaEntity {
    private TipoServico tipoServico; //Atributo código fk da classe TipoServico
    private Peca peca; //Atributo código fk da classe Peca    
}