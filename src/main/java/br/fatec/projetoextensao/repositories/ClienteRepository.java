package br.fatec.projetoextensao.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;

@Repository
public interface ClienteRepository extends JpaRepository<ClienteEntity, Integer> {

    boolean existsByFonte(FonteAquisicaoEntity fonte);

    List<ClienteEntity> findByPessoaNomeContainingIgnoreCaseOrderByPessoaNome(String trecho);

    List<ClienteEntity> findAllByOrderByPessoaNome();

}
