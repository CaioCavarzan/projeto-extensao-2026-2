package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Producao")
public class ProducaoEntity{

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;

    @Column(name = "cronograma_codigo")
    private CronogramaEntity cronograma; //Atributo código fk da classe Cronograma 

    @Column(name = "peca_tipo_servico")
    private ServicoAplicadoPecaEntity servicoAplicadoPeca; //Atributo código fk da classe ServicoAplicadoPeca

    @Column(name = "peca_codigo")
    private PecaEntity peca;    
    
    @Column(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou;

    @Column(name = "data_execucao")  
    private LocalDateTime dataExecucao;

    @Column(name = "qtd_produzida")
    private int qtdProduzida;

    @Column(name = "qtd_defeito")
    private int qtdDefeito;

    @Column(name = "observacao")
    private String observacao;

}