package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.PessoaEntity;
import br.fatec.projetoextensao.entities.EnderecoEntity;

@Repository
public interface PessoaRepository extends JpaRepository<PessoaEntity, Integer> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndCodigoNot(String email, int codigo);

    long countByEndereco(EnderecoEntity endereco);

}
