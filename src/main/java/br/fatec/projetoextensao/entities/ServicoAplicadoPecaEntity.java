package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;

@Entity
public class ServicoAplicadoPecaEntity {
    private TipoServicoEntity tipoServico; //Atributo código fk da classe TipoServico
    private PecaEntity peca; //Atributo código fk da classe Peca    
}

//a