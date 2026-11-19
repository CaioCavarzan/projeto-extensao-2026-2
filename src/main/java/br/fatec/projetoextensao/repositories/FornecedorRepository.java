package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FornecedorEntity;

@Repository
public interface FornecedorRepository extends JpaRepository<FornecedorEntity, Integer> {

    Optional<FornecedorEntity> findByCnpj(String cnpj);

    boolean existsByCnpj(String cnpj);

    List<FornecedorEntity> findAllByOrderByPessoaNome();

    List<FornecedorEntity> findByAtivoTrueOrderByPessoaNome();

}
