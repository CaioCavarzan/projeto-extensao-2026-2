package br.fatec.projetoextensao.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;

@Repository
public interface FonteAquisicaoRepository extends JpaRepository<FonteAquisicaoEntity, Integer> {
    
}
