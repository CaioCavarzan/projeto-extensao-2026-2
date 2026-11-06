package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FornecedorEntity;

@Repository
public interface FornecedorRepository extends JpaRepository<FornecedorEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-PES-02: localizar o fornecedor pelo CNPJ para ligar o papel de cliente PJ à mesma pessoa.
    Optional<FornecedorEntity> findByCnpj(String cnpj);

    // INCLUÍDO (20/10/2026): RN-FOR-05: CNPJ único entre fornecedores (UK uq_fornecedores_cnpj).
    boolean existsByCnpj(String cnpj);

    // INCLUÍDO (20/10/2026): Listagem em ordem alfabética.
    List<FornecedorEntity> findAllByOrderByPessoaNome();

    // INCLUÍDO (20/10/2026): RN-FOR-03: só fornecedor ativo é oferecido para tinta nova.
    List<FornecedorEntity> findByAtivoTrueOrderByPessoaNome();

}
