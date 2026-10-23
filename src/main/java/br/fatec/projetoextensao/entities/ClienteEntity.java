package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "Clientes")
public abstract class ClienteEntity{

    @Id
    private int pessoaCodigo;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "pessoa_codigo")
    private PessoaEntity pessoa;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fonte_codigo")
    private FonteAquisicaoEntity fonte;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "prioridade")
    private Integer prioridade;

}
