package br.fatec.projetoextensao.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
//import java.util.ArrayList;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Cronograma")
public class CronogramaEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;

    @Column(name = "peca_tipo_servico")
    private ServicoAplicadoPecaEntity servicoAplicadoPeca;

    @Column(name = "peca_codigo")
    private PecaEntity peca;

    @Column(name = "data_planejada")
    private LocalDate dataPlanejada;

    @Column(name = "qtd_planejada")
    private int qtdPlanejada;

    @Column(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou; //Atributo codigo fk da classe Usuario
    
    @Column(name = "observacao")
    private String observacao;

    @Column(name = "prioridade")
    private int prioridade;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "ultima_alteracao")
    private LocalDateTime ultimaAlteracao;

    @Column(name = "usuario_alteracao")
    private UsuarioEntity usuarioAlterou; //Atributo codigo fk da classe Usuario
    
    //Não constava na DER
    //@Column(name = "historico")
    //private ArrayList<HistoricoStatusCronogramaEntity> historico;
}
