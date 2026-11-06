package br.fatec.projetoextensao.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.PessoaFisicaEntity;

@Repository
public interface PessoaFisicaRepository extends JpaRepository<PessoaFisicaEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-PES-02: localizar a pessoa pelo CPF para ligar o papel de funcionário à mesma pessoa.
    Optional<PessoaFisicaEntity> findByCpf(String cpf);

    // INCLUÍDO (20/10/2026): RN-PF-03: CPF único entre clientes PF (UK uq_pessoas_fisicas_cpf).
    boolean existsByCpf(String cpf);

}
