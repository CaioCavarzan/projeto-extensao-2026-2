package br.fatec.projetoextensao.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Pecas")
public class PecaEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;
    
    @Column(name = "os_codigo")
    private OrdemServicoEntity os; //atributo codigo fk da classe Os;

    @Column(name = "tinta_codigo")
    private TintaEntity tinta; //atributo codigo fk da classe Tinta;

    @Column(name = "ultima_alteracao")
    private LocalDateTime ultimaAlteracao;

    //Não constava na DER
    //@Column(name = "servicos")
    //private ArrayList<TipoServicoEntity> servicos;

    @Column(name = "descricao_peca")
    private String descricaoPeca;

    @Column(name = "qtd_total")
    private int qtdTotal;

    @Column(name = "data_entrada")
    private LocalDate dataEntrada;

    @Column(name = "urgencia")
    private String urgencia;
    
    @Column(name = "prazo_entrega")
    private String prazoEntrega;
    
    @Column(name = "valor_total_peca")
    private float valorTotalPeca;
    
    @Column(name = "data_finalizacao")
    private LocalDate dataFinalizacao;
    
    @Column(name = "numero_ose")
    private String numeroOse;
    
    @Column(name = "score_prioridade")
    private float scorePrioridade;

    //Não constava na DER
    //private ArrayList<HistoricoStatusPecaEntity> historico;
    //private ArrayList<FotoEntity> fotos;
}
