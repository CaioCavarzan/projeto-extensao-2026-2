package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FuncionarioEntity;

@Repository
public interface FuncionarioRepository extends JpaRepository<FuncionarioEntity, Integer> {

    Optional<FuncionarioEntity> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    List<FuncionarioEntity> findAllByOrderByPessoaNome();

}
