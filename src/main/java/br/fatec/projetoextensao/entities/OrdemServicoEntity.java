package br.fatec.projetoextensao.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "OrdensDeServico")
public class OrdemServicoEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nr_os")
    private int numero;

    @ManyToOne (optional = false)
    @JoinColumn(name = "cliente_codigo")
    private ClienteEntity cliente;

    @ManyToOne (optional = false)
    @JoinColumn(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou;
    
    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;
    
    @PrePersist
    private void preencherDataEntrada(){
     if (dataEntrada == null) {
         dataEntrada = LocalDateTime.now();}
     }

    @Column(name = "valor_os")
    private BigDecimal valorOS = BigDecimal.ZERO;

    @Column(name = "prioridade")
    private Integer prioridade;

}
