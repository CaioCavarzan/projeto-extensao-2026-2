package br.fatec.projetoextensao.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "ItensRotasProcessos")
public class ItemRotaProcessoEntity{

    @Column(name = "rota_processo_codigo")
    private RotaProcessoEntity rotaProcesso;

    @Column(name = "item_codigo")
    private ItemEntity item;

    @Column(name = "ordem")
    private int ordem;

    @Column(name = "data_inicio")
    private LocalDateTime dataInicio;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    @Column(name = "funcionario_codigo")
    private FuncionarioEntity funcionario;

}