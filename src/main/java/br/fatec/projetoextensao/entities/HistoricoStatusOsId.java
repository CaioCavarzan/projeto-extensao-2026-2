package br.fatec.projetoextensao.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoStatusOsId implements Serializable {
    
    private int os;
    private int status;
    private LocalDateTime dataHora;
}