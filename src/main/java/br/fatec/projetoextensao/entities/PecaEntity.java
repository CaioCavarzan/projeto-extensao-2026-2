package br.fatec.projetoextensao.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PecaEntity{
    
    @Id
    private int codigo;
    private OrdemServicoEntity os; //atributo codigo fk da classe Os;
    private TintaEntity tinta; //atributo codigo fk da classe Tinta;
    private ArrayList<TipoServicoEntity> servicos;
    private String descricaoPeca;
    private int qtdTotal;
    private LocalDateTime dataEntrada;
    private String urgencia;
    private String prazoEntrega;
    private float valorTotalPeca;
    private LocalDate dataFinalizacao;
    private String numeroOse;
    private float scorePrioridade;
    private LocalDateTime ultimaAlteracao;
    private ArrayList<HistoricoStatusPecaEntity> historico;
    private ArrayList<FotoEntity> fotos;
}
