package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FuncionarioEntity;

@Repository
public interface FuncionarioRepository extends JpaRepository<FuncionarioEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-PES-02 e RN-USU-02: localizar o funcionário pelo CPF (ligar o papel de cliente; ativar a conta).
    Optional<FuncionarioEntity> findByCpf(String cpf);

    // INCLUÍDO (20/10/2026): RN-FUN-04: CPF único entre funcionários (UK uq_funcionarios_cpf).
    boolean existsByCpf(String cpf);

    // INCLUÍDO (20/10/2026): Listagem em ordem alfabética.
    List<FuncionarioEntity> findAllByOrderByPessoaNome();

}
