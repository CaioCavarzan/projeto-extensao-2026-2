package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Pecas_TipoServico")
public class ServicoAplicadoPecaEntity{

    @Column(name = "tipo_servico_codigo")
    private TipoServicoEntity tipoServico; //Atributo código fk da classe TipoServico
    
    @Column(name = "peca_codigo")
    private PecaEntity peca; //Atributo código fk da classe Peca    
}