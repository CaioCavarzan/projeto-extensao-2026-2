package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.EnderecoEntity;

@Repository
public interface PessoaRepository extends JpaRepository<PessoaEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-PES-10: e-mail único quando informado (o índice UX_Pessoas_email não diferencia maiúsculas no SQL Server).
    boolean existsByEmailIgnoreCase(String email);

    // INCLUÍDO (20/10/2026): RN-PES-10: na alteração, o e-mail pode ser o da própria pessoa.
    boolean existsByEmailIgnoreCaseAndCodigoNot(String email, int codigo);

    // INCLUÍDO (20/10/2026): RN-END-01 e RN-END-02: quantas pessoas usam o endereço (endereço sem dono é excluído; endereço dividido não é alterado no lugar).
    long countByEndereco(EnderecoEntity endereco);

}
