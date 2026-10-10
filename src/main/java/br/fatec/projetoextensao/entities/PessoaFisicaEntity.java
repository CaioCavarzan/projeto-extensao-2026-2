package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@PrimaryKeyJoinColumn(name = "cliente_codigo")
@Table(name = "PessoasFisicas")
public class PessoaFisicaEntity extends ClienteEntity{

    @Column(name = "cpf")
    private String cpf;

    @Column(name = "nome_social")
    private String nomeSocial;

}
