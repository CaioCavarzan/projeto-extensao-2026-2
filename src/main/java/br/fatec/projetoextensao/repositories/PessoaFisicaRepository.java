package br.fatec.projetoextensao.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.PessoaFisicaEntity;

@Repository
public interface PessoaFisicaRepository extends JpaRepository<PessoaFisicaEntity, Integer> {

    Optional<PessoaFisicaEntity> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

}
