package br.fatec.projetoextensao.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FonteAquisicaoEntity{

    @Id
    private int codigo;
    private String descricao;
}
