package br.fatec.projetoextensao.entities;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor
@Data
@Table(name = "Cronogramas")
public class CronogramaEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou; 

    @Column(name = "data_planejada")
    private LocalDate dataPlanejada;

    @Column(name = "observacao")
    private String observacao;

}
