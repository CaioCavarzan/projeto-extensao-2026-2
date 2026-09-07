package br.fatec.projetoextensao.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class CronogramaEntity{

    @Id
    private int codigo;
    private LocalDate dataPlanejada;
    private int qtdPlanejada;
    private UsuarioEntity usuarioCadastrou; //Atributo codigo fk da classe Usuario
    private String observacao;
    private int prioridade;
    private LocalDateTime dataCriacao;
    private LocalDateTime ultimaAlteracao;
    private UsuarioEntity usuarioAlterou; //Atributo codigo fk da classe Usuario
    private ServicoAplicadoPecaEntity servicoAplicadoPeca;
    private ArrayList<HistoricoStatusCronogramaEntity> historico;
}
