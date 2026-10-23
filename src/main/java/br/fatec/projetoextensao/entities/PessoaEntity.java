package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
// REMOVIDO (20/10/2026): os imports de Inheritance e InheritanceType, que só serviam para a anotação retirada abaixo.
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
// REMOVIDO (20/10/2026): @Inheritance(strategy = InheritanceType.JOINED).
// Motivo: regra de especialização do grupo (20/10). A mesma pessoa pode ser cliente e funcionário, ou cliente e
// fornecedor, ao mesmo tempo. Com herança, um objeto Java tem uma classe só: o funcionário que virava cliente era
// gravado como OUTRA pessoa (outra linha em Pessoas, com o mesmo CPF em Funcionarios e em PessoasFisicas), e a
// pessoa com dois papéis não era lida nos dois. Agora Funcionário, Cliente e Fornecedor apontam para a Pessoa
// (composição com @OneToOne + @MapsId), e cada papel é uma linha na sua tabela com o mesmo código da pessoa.
// O banco não muda: as tabelas, as chaves e as FKs do script continuam exatamente as mesmas.
@Table(name = "Pessoas")
public class PessoaEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo")
    private int codigo;

    @Column(name = "nome")
    private String nome;

    @Column(name = "email")
    private String email;

    @Column(name = "telefone")
    private String telefone;

    @ManyToOne(optional = false)
    @JoinColumn(name = "endereco_codigo")
    private EnderecoEntity endereco;

}
