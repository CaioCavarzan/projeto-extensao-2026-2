package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class OrdemServicoEntity{

    @Id
    private int numero;
    private ClienteEntity cliente; // atributo codigo fk da classe Cliente;
    private UsuarioEntity usuarioCadastrou; // atributo codigo fk da classe Usuario;
    private LocalDateTime dataEntrada;
    private float valorOS;
    private String prioridade;
    private ArrayList<PecaEntity> listaPeca;
    private ArrayList<HistoricoStatusOsEntity> historico;
}
