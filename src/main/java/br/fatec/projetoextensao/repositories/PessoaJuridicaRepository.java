package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.PessoaJuridicaEntity;

@Repository
public interface PessoaJuridicaRepository extends JpaRepository<PessoaJuridicaEntity, Integer> {
    
}
