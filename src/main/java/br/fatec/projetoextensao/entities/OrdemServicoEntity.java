package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "OrdensDeServico")
public class OrdemServicoEntity{

    @Id
    @Column(name = "nr_os")
    private int numero;

    @Column(name = "cliente_codigo")
    private ClienteEntity cliente; // atributo codigo fk da classe Cliente;

    @Column(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou; // atributo codigo fk da classe Usuario;
    
    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;

    @Column(name = "valor_os")
    private float valorOS;

    @Column(name = "prioridade")
    private String prioridade;

    //Não constava na DER
    //@Column(name = "lista_peca")
    //private ArrayList<PecaEntity> listaPeca;

    //Não constava na DER
    //@Column(name = "historico")
    //private ArrayList<HistoricoStatusOsEntity> historico;
}
