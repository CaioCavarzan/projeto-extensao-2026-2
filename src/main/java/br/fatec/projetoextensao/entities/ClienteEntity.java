package br.fatec.projetoextensao.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
// INCLUÍDO (20/10/2026): imports de Id, MapsId e OneToOne, usados na ligação com a Pessoa (composição).
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
// MANTIDO: @Inheritance(JOINED). Agora ClienteEntity é a raiz da herança Cliente -> {PessoaFisica, PessoaJuridica}.
// Regra do grupo: todo cliente é PF ou PJ e nunca os dois (total e exclusiva). A herança garante o "nunca os dois"
// (um objeto é PessoaFisicaEntity ou PessoaJuridicaEntity) e o abstract garante o "sempre um dos dois" (não existe
// new ClienteEntity()). O ClienteService confere também no banco (RN-CLI-01, RN-PF-01 e RN-PJ-01).
@Inheritance(strategy = InheritanceType.JOINED)
// REMOVIDO (20/10/2026): @PrimaryKeyJoinColumn(name = "pessoa_codigo"). Ela ligava Clientes a Pessoas pela herança;
// agora a mesma coluna é ligada pela composição (@MapsId logo abaixo).
@Table(name = "Clientes")
// ALTERADO (20/10/2026): "extends PessoaEntity" foi retirado; o cliente passou a APONTAR para a pessoa (campo pessoa).
// Motivo: regra de especialização do grupo (20/10): a pessoa pode ser cliente e funcionário, ou cliente e
// fornecedor, ao mesmo tempo. Com herança, o funcionário que vira cliente era gravado como outra pessoa.
// A classe continua abstract (Cliente sempre é PF ou PJ).
public abstract class ClienteEntity{

    // INCLUÍDO (20/10/2026): a chave do cliente é o código da pessoa (coluna pessoa_codigo, PK e FK para Pessoas,
    // como no script). O valor é copiado da pessoa ligada abaixo pelo @MapsId; nunca é digitado (RN-CLI-04).
    @Id
    private int pessoaCodigo;

    // INCLUÍDO (20/10/2026): ligação com a pessoa. @MapsId usa a mesma coluna pessoa_codigo como chave e como FK,
    // do mesmo jeito que UsuarioEntity já faz com FuncionarioEntity. A tabela Clientes não muda.
    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "pessoa_codigo")
    private PessoaEntity pessoa;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fonte_codigo")
    private FonteAquisicaoEntity fonte;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "prioridade")
    private Integer prioridade;

}
