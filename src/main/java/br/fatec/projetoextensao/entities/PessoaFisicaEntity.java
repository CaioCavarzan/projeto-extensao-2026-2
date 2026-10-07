package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor 
@Table(name = "PessoasFisicas")
public class PessoaFisicaEntity extends ClienteEntity {

    @Column(name = "cliente_codigo")
    private ClienteEntity cliente;

    @Column(name = "cpf")
    private String cpf;

}
