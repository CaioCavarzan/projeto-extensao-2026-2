package br.fatec.projetoextensao.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "OrdensDeServico")
public class OrdemServicoEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nr_os")
    private int numero;

    @ManyToOne (optional = false)
    @JoinColumn(name = "cliente_codigo")
    private ClienteEntity cliente;

    @ManyToOne (optional = false)
    @JoinColumn(name = "usuario_codigo")
    private UsuarioEntity usuarioCadastrou;
    
    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;
    
    @PrePersist
    private void preencherDataEntrada(){
     if (dataEntrada == null) {
         dataEntrada = LocalDateTime.now();}
     }

    // ALTERADO (18/11/2026) [E3-07]: era @Column(name = "valor_os"), gravado pelo Java (ValorOsService.recalcular).
    // Agora quem grava valor_os é o banco: os triggers trg_itens_sincronizar_valor_os e
    // trg_historicos_status_itens_sincronizar_item_e_os (e a procedure sp_cancelar_ordem_servico) recalculam o valor
    // com dbo.fn_valor_os a cada mudança de item. insertable = false e updatable = false fazem o Hibernate NUNCA
    // escrever esta coluna. Por quê: o Hibernate manda todas as colunas no UPDATE da OS (ex.: ao mudar a prioridade);
    // sem isso, ele regravaria o valor antigo que tem na memória por cima do valor que o trigger acabou de calcular.
    // Na abertura a coluna fica fora do INSERT e vale o DEFAULT 0 do script. O valor atualizado é relido com
    // ValorOsService.sincronizar (EntityManager.refresh).
    @Column(name = "valor_os", insertable = false, updatable = false)
    private BigDecimal valorOS = BigDecimal.ZERO;

    @Column(name = "prioridade")
    private Integer prioridade;

}
