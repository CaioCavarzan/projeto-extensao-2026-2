package br.fatec.projetoextensao.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
// INCLUÍDO (20/10/2026): imports de Id, JoinColumn, MapsId e OneToOne, usados na ligação com a Pessoa (composição).
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
// REMOVIDO (20/10/2026): import de PrimaryKeyJoinColumn, que só servia para a herança de PessoaEntity.
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
// REMOVIDO (20/10/2026): @PrimaryKeyJoinColumn(name = "pessoa_codigo"). A coluna continua a mesma; agora é ligada
// pela composição (@MapsId logo abaixo).
@Table(name = "Funcionarios")
// ALTERADO (20/10/2026): "extends PessoaEntity" foi retirado; o funcionário passou a APONTAR para a pessoa.
// Motivo: regra de especialização do grupo (20/10): a mesma pessoa pode ser funcionário e cliente ao mesmo tempo.
// Com herança, o funcionário que virava cliente era gravado como outra pessoa (outro código em Pessoas).
// A regra "funcionário não pode ser fornecedor" é conferida no FuncionarioService e no FornecedorService (RN-PES-05).
public class FuncionarioEntity{

    // INCLUÍDO (20/10/2026): a chave do funcionário é o código da pessoa (coluna pessoa_codigo, PK e FK para
    // Pessoas, como no script). O valor é copiado da pessoa ligada abaixo pelo @MapsId (RN-FUN-03).
    @Id
    private int pessoaCodigo;

    // INCLUÍDO (20/10/2026): ligação com a pessoa (nome, contato e endereço ficam em Pessoas).
    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "pessoa_codigo")
    private PessoaEntity pessoa;

    @Column(name = "cpf")
    private String cpf;

    @Column(name = "cargo")
    private String cargo;

    @Column(name = "salario")
    private BigDecimal salario;

    @Column(name = "data_admissao")
    private LocalDate dataAdmissao;

}
