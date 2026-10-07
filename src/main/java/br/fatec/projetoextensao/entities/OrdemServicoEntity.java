package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor 
@AllArgsConstructor 
@Table(name = "OrdensDeServico")
public class OrdemServicoEntity{

    @Id
    @Column(name = "nr_os")
    private int numero;

    @Column(name = "cliente_codigo")
    private ClienteEntity cliente;

    @Column(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou;
    
    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;

    @Column(name = "valor_os")
    private float valorOS;

    @Column(name = "prioridade")
    private String prioridade;

}
