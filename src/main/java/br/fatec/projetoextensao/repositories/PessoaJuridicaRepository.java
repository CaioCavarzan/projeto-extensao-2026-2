package br.fatec.projetoextensao.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.PessoaJuridicaEntity;

@Repository
public interface PessoaJuridicaRepository extends JpaRepository<PessoaJuridicaEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-PES-02: localizar a pessoa pelo CNPJ para ligar o papel de fornecedor à mesma pessoa.
    Optional<PessoaJuridicaEntity> findByCnpj(String cnpj);

    // INCLUÍDO (20/10/2026): RN-PJ-03: CNPJ único entre clientes PJ (UK uq_pessoas_juridicas_cnpj).
    boolean existsByCnpj(String cnpj);

}
