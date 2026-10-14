package br.fatec.projetoextensao.entities;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CronogramaItemId implements Serializable {
    
    private int cronograma;   
    private int itemRotaProcesso;
}
