package br.fatec.projetoextensao.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoStatusItemId implements Serializable {
    
    private int item;
    private int statusItem;
    private LocalDateTime dataHora;
}
