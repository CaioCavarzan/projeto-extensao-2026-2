package br.fatec.projetoextensao.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.CorEntity;

@Repository
public interface CorRepository extends JpaRepository<CorEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-COR-03: nome único (UK uq_cores_nome).
    boolean existsByNomeIgnoreCase(String nome);

    // INCLUÍDO (20/10/2026): RN-COR-03: na renomeação, o nome pode ser o da própria cor.
    boolean existsByNomeIgnoreCaseAndCodigoNot(String nome, int codigo);

    // INCLUÍDO (20/10/2026): Listagem em ordem alfabética.
    List<CorEntity> findAllByOrderByNome();

}
